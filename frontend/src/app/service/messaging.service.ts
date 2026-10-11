import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { BehaviorSubject } from 'rxjs';
import { Client, StompSubscription } from '@stomp/stompjs';
import SockJS from 'sockjs-client';
import { environment } from '../../environments/environment';

export interface ConversationDto {
  id: number;
  otherUserId: number | null;
  otherDisplayName: string | null;
  lastMessage: string | null;
  lastMessageAt: string | null;
  productId: number | null;
  productName: string | null;
  productImageUrl: string | null;
  unreadCount: number;
  /** The viewer blocked the other user. */
  blockedByMe: boolean;
  /** Either user blocked the other; no new messages can be sent. */
  blocked: boolean;
}

export interface MessageDto {
  id: number;
  conversationId: number;
  senderId: number;
  body: string;
  createdAt: string;
}

export interface SendMessageRequest {
  conversationId: number | null;
  recipientId: number | null;
  body: string;
}

@Injectable({ providedIn: 'root' })
export class MessagingService {
  private stompClient: Client | null = null;
  private connectPromise: Promise<void> | null = null;
  private connected = false;
  private subscription: StompSubscription | null = null;
  private watcher: ConversationWatcher | null = null;
  /** Total unread messages, shown as the navbar badge. */
  readonly unreadCount$ = new BehaviorSubject<number>(0);

  constructor(private http: HttpClient) {}

  unreadCount() {
    return this.http.get<number>(`${environment.apiUrl}/conversations/unread-count`);
  }

  setUnreadCount(count: number): void {
    this.unreadCount$.next(Math.max(0, count));
  }

  markRead(conversationId: number) {
    return this.http.post<void>(`${environment.apiUrl}/conversations/${conversationId}/read`, {});
  }

  listConversations() {
    return this.http.get<ConversationDto[]>(`${environment.apiUrl}/conversations`);
  }

  getMessages(conversationId: number, limit = 50) {
    return this.http.get<MessageDto[]>(
      `${environment.apiUrl}/conversations/${conversationId}/messages?limit=${limit}`
    );
  }

  createConversationWith(userId: number, productId?: number | null) {
    const params: Record<string, string> = productId ? { productId: String(productId) } : {};
    return this.http.post<ConversationDto>(`${environment.apiUrl}/conversations/with/${userId}`, {}, { params });
  }

  sendMessage(conversationId: number, recipientId: number | null, body: string) {
    const payload: SendMessageRequest = {
      conversationId,
      recipientId,
      body
    };
    return this.http.post<MessageDto>(
      `${environment.apiUrl}/conversations/${conversationId}/messages`,
      payload
    );
  }

  /**
   * Streams new messages of one conversation (the one on screen) to {@code handler}. The
   * subscription is restored after every reconnect, and {@code onResync} lets the caller reload
   * messages that arrived while the socket was down. Returns a function that stops watching.
   */
  watchConversation(
    conversationId: number,
    handler: (message: MessageDto) => void,
    onResync?: () => void
  ): () => void {
    const watcher: ConversationWatcher = { conversationId, handler, onResync, subscribedOnce: false };
    this.watcher = watcher;
    this.subscribeWatcher();
    this.connect().catch(() => {
      // No live updates; the REST calls keep working and the client keeps retrying.
    });
    return () => {
      if (this.watcher === watcher) {
        this.watcher = null;
        this.unsubscribeCurrent();
      }
    };
  }

  disconnect(): void {
    this.watcher = null;
    this.unsubscribeCurrent();
    this.stompClient?.deactivate();
    this.stompClient = null;
    this.connected = false;
    this.connectPromise = null;
  }

  private connect(): Promise<void> {
    if (this.connected) {
      return Promise.resolve();
    }
    if (this.connectPromise) {
      return this.connectPromise;
    }
    this.connectPromise = new Promise((resolve, reject) => {
      this.stompClient = new Client({
        webSocketFactory: () => new SockJS(`${resolveWsBase()}/ws`),
        reconnectDelay: 5000,
        onConnect: () => {
          this.connected = true;
          resolve();
          // stompjs reconnects on its own but does not restore subscriptions.
          this.subscribeWatcher();
        },
        onWebSocketClose: () => {
          this.connected = false;
          this.subscription = null;
        },
        onStompError: () => {
          this.connected = false;
          // Do not cache the failure: the next call may connect.
          this.connectPromise = null;
          reject(new Error('WebSocket connection failed.'));
        }
      });
      this.stompClient.activate();
    });
    return this.connectPromise;
  }

  private subscribeWatcher(): void {
    const watcher = this.watcher;
    if (!watcher || !this.stompClient || !this.connected) {
      return;
    }
    this.unsubscribeCurrent();
    this.subscription = this.stompClient.subscribe(`/topic/conversations/${watcher.conversationId}`, message => {
      watcher.handler(JSON.parse(message.body) as MessageDto);
    });
    if (watcher.subscribedOnce) {
      watcher.onResync?.();
    }
    watcher.subscribedOnce = true;
  }

  private unsubscribeCurrent(): void {
    try {
      this.subscription?.unsubscribe();
    } catch {
      // The connection is already gone, and the subscription with it.
    }
    this.subscription = null;
  }
}

interface ConversationWatcher {
  conversationId: number;
  handler: (message: MessageDto) => void;
  onResync?: () => void;
  /** After the first subscription, a new one means a reconnect happened. */
  subscribedOnce: boolean;
}

function resolveWsBase(): string {
  if (environment.apiUrl.endsWith('/api')) {
    return environment.apiUrl.slice(0, -4);
  }
  return environment.apiUrl;
}
