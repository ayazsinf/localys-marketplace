import { Component, OnDestroy, OnInit } from '@angular/core';
import { MessagingService, ConversationDto, ConversationEvent, MessageDto } from '../../service/messaging.service';
import { UserService, UserProfile } from '../../service/user.service';
import { ActivatedRoute } from '@angular/router';
import { TranslateService } from '@ngx-translate/core';
import { getLocaleForLanguage } from '../../shared/market';
import { BlockService } from '../../service/block.service';

/** How long "typing..." stays up without a new signal, and how often we send ours. */
const TYPING_VISIBLE_MS = 4000;
const TYPING_SEND_INTERVAL_MS = 2500;

type MessageItem = {
  id: number;
  senderId: number;
  text: string;
  timestamp: string;
  fromMe: boolean;
  safetyWarning: string | null;
  readAt: string | null;
};

type Conversation = {
  id: number;
  otherUserId: number | null;
  name: string;
  lastMessage: string;
  lastTime: string;
  unreadCount: number;
  productId: number | null;
  productName: string | null;
  productImageUrl: string | null;
  blockedByMe: boolean;
  blocked: boolean;
  messages: MessageItem[];
};

@Component({
  selector: 'app-messages',
  templateUrl: './messages.component.html',
  styleUrl: './messages.component.scss',
  standalone: false
})
export class MessagesComponent implements OnInit, OnDestroy {
  conversations: Conversation[] = [];
  selectedConversationId: number | null = null;
  searchQuery = '';
  draftMessage = '';
  sendError: string | null = null;
  isBlockBusy = false;
  /** The other user of the open conversation is typing. */
  otherTyping = false;
  private typingTimer: ReturnType<typeof setTimeout> | null = null;
  private lastTypingSentAt = 0;
  currentUser: UserProfile | null = null;
  /** Stops the live updates of the open conversation. */
  private stopWatching: (() => void) | null = null;
  private requestedConversationId: number | null = null;

  constructor(
    private messagingService: MessagingService,
    private userService: UserService,
    private route: ActivatedRoute,
    private translateService: TranslateService,
    private blockService: BlockService
  ) {}

  ngOnInit(): void {
    this.requestedConversationId = Number(this.route.snapshot.queryParamMap.get('conversationId'));
    if (!Number.isFinite(this.requestedConversationId)) {
      this.requestedConversationId = null;
    }

    this.userService.getMe().subscribe(profile => {
      this.currentUser = profile;
      this.loadConversations();
    });
  }

  ngOnDestroy(): void {
    this.clearTyping();
    this.stopWatching?.();
    this.messagingService.disconnect();
  }

  get filteredConversations(): Conversation[] {
    const query = this.searchQuery.trim().toLowerCase();
    if (!query) {
      return this.conversations;
    }
    return this.conversations.filter(conversation =>
      conversation.name.toLowerCase().includes(query) ||
      (conversation.productName ?? '').toLowerCase().includes(query) ||
      conversation.lastMessage.toLowerCase().includes(query)
    );
  }

  get selectedConversation(): Conversation | undefined {
    return this.conversations.find(item => item.id === this.selectedConversationId);
  }

  /** The last of my messages the other user has read, to show "Seen" under it. */
  get lastSeenMessageId(): number | null {
    const messages = this.selectedConversation?.messages ?? [];
    for (let i = messages.length - 1; i >= 0; i--) {
      if (messages[i].fromMe) {
        return messages[i].readAt ? messages[i].id : null;
      }
    }
    return null;
  }

  onDraftChange(value: string): void {
    this.draftMessage = value;
    const conversation = this.selectedConversation;
    const now = Date.now();
    if (conversation && !conversation.blocked && value.trim() && now - this.lastTypingSentAt > TYPING_SEND_INTERVAL_MS) {
      this.lastTypingSentAt = now;
      this.messagingService.sendTyping(conversation.id);
    }
  }

  selectConversation(conversation: Conversation): void {
    this.clearTyping();
    this.selectedConversationId = conversation.id;
    conversation.unreadCount = 0;
    this.loadMessages(conversation);
  }

  sendMessage(): void {
    const conversation = this.selectedConversation;
    const text = this.draftMessage.trim();
    if (!conversation || !text || !conversation.otherUserId || conversation.blocked) {
      return;
    }
    this.sendError = null;
    this.messagingService.sendMessage(conversation.id, conversation.otherUserId, text).subscribe({
      next: message => {
        this.handleIncomingMessage(message);
        this.draftMessage = '';
      },
      error: err => {
        if (err?.error?.code === 'USER_BLOCKED') {
          conversation.blocked = true;
        } else {
          this.sendError = 'MESSAGES.SEND_ERROR';
        }
      }
    });
  }

  toggleBlock(): void {
    const conversation = this.selectedConversation;
    if (!conversation?.otherUserId || this.isBlockBusy) {
      return;
    }
    const blocking = !conversation.blockedByMe;
    if (blocking && !confirm(this.translateService.instant('BLOCK.CONFIRM', { name: conversation.name }))) {
      return;
    }
    this.isBlockBusy = true;
    const otherUserId = conversation.otherUserId;
    const request = blocking ? this.blockService.block(otherUserId) : this.blockService.unblock(otherUserId);
    request.subscribe({
      next: () => {
        this.isBlockBusy = false;
        // The other user may have blocked us too, so ask the server for the real state.
        this.messagingService.listConversations().subscribe(list => {
          for (const dto of list) {
            const known = this.conversations.find(item => item.id === dto.id);
            if (known) {
              known.blockedByMe = dto.blockedByMe;
              known.blocked = dto.blocked;
            }
          }
        });
      },
      error: () => {
        this.isBlockBusy = false;
      }
    });
  }

  trackByConversation(_: number, conversation: Conversation): number {
    return conversation.id;
  }

  trackByMessage(_: number, message: MessageItem): number {
    return message.id;
  }

  private loadConversations(): void {
    this.messagingService.listConversations().subscribe(conversations => {
      this.conversations = conversations.map(item => this.toConversation(item));
      if (this.conversations.length > 0) {
        const requested = this.requestedConversationId;
        const next = requested && this.conversations.some(conv => conv.id === requested)
          ? requested
          : (this.selectedConversationId ?? this.conversations[0].id);
        this.selectedConversationId = next;
        const active = this.conversations.find(conv => conv.id === next);
        if (active) {
          this.loadMessages(active);
        }
      }
    });
  }

  private loadMessages(conversation: Conversation): void {
    this.messagingService.getMessages(conversation.id).subscribe(messages => {
      conversation.messages = messages.map(message => this.toMessage(message));
      conversation.unreadCount = 0;
      this.refreshGlobalUnreadCount();
      this.bindConversationChannel(conversation.id);
    });
  }

  private bindConversationChannel(conversationId: number): void {
    this.stopWatching?.();
    this.stopWatching = this.messagingService.watchConversation(
      conversationId,
      message => this.handleIncomingMessage(message),
      // After a reconnect, fetch what was sent while the socket was down.
      () => this.resyncMessages(conversationId),
      event => this.handleEvent(event)
    );
  }

  private handleEvent(event: ConversationEvent): void {
    if (!this.currentUser || event.userId === this.currentUser.id || event.conversationId !== this.selectedConversationId) {
      return;
    }
    if (event.type === 'TYPING') {
      this.otherTyping = true;
      if (this.typingTimer) {
        clearTimeout(this.typingTimer);
      }
      this.typingTimer = setTimeout(() => this.clearTyping(), TYPING_VISIBLE_MS);
      return;
    }
    const conversation = this.conversations.find(item => item.id === event.conversationId);
    for (const message of conversation?.messages ?? []) {
      if (message.fromMe && !message.readAt) {
        message.readAt = event.at;
      }
    }
  }

  private clearTyping(): void {
    this.otherTyping = false;
    if (this.typingTimer) {
      clearTimeout(this.typingTimer);
      this.typingTimer = null;
    }
  }

  private resyncMessages(conversationId: number): void {
    const conversation = this.conversations.find(item => item.id === conversationId);
    if (!conversation) {
      return;
    }
    this.messagingService.getMessages(conversationId).subscribe(messages => {
      conversation.messages = messages.map(message => this.toMessage(message));
    });
  }

  private handleIncomingMessage(message: MessageDto): void {
    const conversation = this.conversations.find(item => item.id === message.conversationId);
    if (!conversation) {
      return;
    }

    if (conversation.messages.some(existing => existing.id === message.id)) {
      return;
    }

    const mapped = this.toMessage(message);
    if (!mapped.fromMe && conversation.id === this.selectedConversationId) {
      this.clearTyping();
    }
    conversation.messages.push(mapped);
    conversation.lastMessage = mapped.text;
    conversation.lastTime = mapped.timestamp;

    if (this.selectedConversationId !== conversation.id) {
      conversation.unreadCount += 1;
    } else if (!mapped.fromMe) {
      // The conversation is open, so the message has been seen.
      this.messagingService.markRead(conversation.id).subscribe({
        next: () => this.refreshGlobalUnreadCount(),
        error: () => {}
      });
    }
  }

  private refreshGlobalUnreadCount(): void {
    this.messagingService.unreadCount().subscribe({
      next: count => this.messagingService.setUnreadCount(count),
      error: () => {}
    });
  }

  private toConversation(dto: ConversationDto): Conversation {
    const displayName = dto.otherDisplayName || this.translateService.instant('PROFILE_PUBLIC.MEMBER');
    return {
      id: dto.id,
      otherUserId: dto.otherUserId,
      name: displayName,
      lastMessage: dto.lastMessage || '',
      lastTime: dto.lastMessageAt ? this.formatTime(dto.lastMessageAt) : '',
      unreadCount: dto.unreadCount ?? 0,
      productId: dto.productId,
      productName: dto.productName,
      productImageUrl: dto.productImageUrl,
      blockedByMe: dto.blockedByMe,
      blocked: dto.blocked,
      messages: []
    };
  }

  private toMessage(dto: MessageDto): MessageItem {
    const fromMe = this.currentUser ? dto.senderId === this.currentUser.id : false;
    return {
      id: dto.id,
      senderId: dto.senderId,
      text: dto.body,
      timestamp: this.formatTime(dto.createdAt),
      fromMe,
      safetyWarning: dto.safetyWarning ?? null,
      readAt: dto.readAt ?? null
    };
  }

  private formatTime(value: string): string {
    const date = new Date(value);
    if (Number.isNaN(date.getTime())) {
      return '';
    }
    return date.toLocaleString(getLocaleForLanguage(
      this.translateService.currentLang || this.translateService.getDefaultLang()
    ));
  }
}
