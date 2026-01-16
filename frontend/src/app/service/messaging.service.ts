import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Client, StompSubscription } from '@stomp/stompjs';
import SockJS from 'sockjs-client';
import { environment } from '../../environments/environment';
import { keycloak } from '../keycloak.service';

export interface ConversationDto {
  id: number;
  otherUserId: number | null;
  otherUsername: string | null;
  otherDisplayName: string | null;
  lastMessage: string | null;
  lastMessageAt: string | null;
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

  constructor(private http: HttpClient) {}

  listConversations() {
    return this.http.get<ConversationDto[]>(`${environment.apiUrl}/api/conversations`);
  }

  getMessages(conversationId: number, limit = 50) {
    return this.http.get<MessageDto[]>(
      `${environment.apiUrl}/api/conversations/${conversationId}/messages?limit=${limit}`
    );
  }

  createConversationWith(userId: number) {
    return this.http.post<ConversationDto>(`${environment.apiUrl}/api/conversations/with/${userId}`, {});
  }

  sendMessage(conversationId: number, recipientId: number | null, body: string) {
    const payload: SendMessageRequest = {
      conversationId,
      recipientId,
      body
    };
    return this.http.post<MessageDto>(
      `${environment.apiUrl}/api/conversations/${conversationId}/messages`,
      payload
    );
  }

  async connect(): Promise<void> {
    if (this.connected) {
      return;
    }
    if (this.connectPromise) {
      return this.connectPromise;
    }

    this.connectPromise = new Promise(async (resolve, reject) => {
      try {
        if (keycloak.token) {
          await keycloak.updateToken(30);
        }
        const token = keycloak.token;
        if (!token) {
          reject(new Error('Missing access token for WebSocket connection.'));
          return;
        }

        this.stompClient = new Client({
          webSocketFactory: () => new SockJS(`${environment.apiUrl}/ws?access_token=${token}`),
          reconnectDelay: 5000,
          onConnect: () => {
            this.connected = true;
            resolve();
          },
          onWebSocketClose: () => {
            this.connected = false;
          },
          onStompError: () => {
            this.connected = false;
            reject(new Error('WebSocket connection failed.'));
          }
        });

        this.stompClient.activate();
      } catch (error) {
        reject(error);
      }
    });

    return this.connectPromise;
  }

  subscribeToConversation(conversationId: number, handler: (message: MessageDto) => void): StompSubscription {
    if (!this.stompClient || !this.connected) {
      throw new Error('WebSocket is not connected.');
    }
    return this.stompClient.subscribe(`/topic/conversations/${conversationId}`, message => {
      const payload = JSON.parse(message.body) as MessageDto;
      handler(payload);
    });
  }

  sendSocketMessage(payload: SendMessageRequest): void {
    if (!this.stompClient || !this.connected) {
      return;
    }
    this.stompClient.publish({
      destination: '/app/messages.send',
      body: JSON.stringify(payload)
    });
  }

  disconnect(): void {
    this.stompClient?.deactivate();
    this.connected = false;
    this.connectPromise = null;
  }
}
