import { Component, OnDestroy, OnInit } from '@angular/core';
import { StompSubscription } from '@stomp/stompjs';
import { MessagingService, ConversationDto, MessageDto } from '../../service/messaging.service';
import { UserService, UserProfile } from '../../service/user.service';
import { ActivatedRoute } from '@angular/router';

type MessageItem = {
  id: number;
  senderId: number;
  text: string;
  timestamp: string;
  fromMe: boolean;
};

type Conversation = {
  id: number;
  otherUserId: number | null;
  name: string;
  lastMessage: string;
  lastTime: string;
  unreadCount: number;
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
  currentUser: UserProfile | null = null;
  private activeSubscription: StompSubscription | null = null;
  private requestedConversationId: number | null = null;

  constructor(
    private messagingService: MessagingService,
    private userService: UserService,
    private route: ActivatedRoute
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
    this.activeSubscription?.unsubscribe();
    this.messagingService.disconnect();
  }

  get filteredConversations(): Conversation[] {
    const query = this.searchQuery.trim().toLowerCase();
    if (!query) {
      return this.conversations;
    }
    return this.conversations.filter(conversation =>
      conversation.name.toLowerCase().includes(query) ||
      conversation.lastMessage.toLowerCase().includes(query)
    );
  }

  get selectedConversation(): Conversation | undefined {
    return this.conversations.find(item => item.id === this.selectedConversationId);
  }

  selectConversation(conversation: Conversation): void {
    this.selectedConversationId = conversation.id;
    conversation.unreadCount = 0;
    this.loadMessages(conversation);
  }

  sendMessage(): void {
    const conversation = this.selectedConversation;
    const text = this.draftMessage.trim();
    if (!conversation || !text || !conversation.otherUserId) {
      return;
    }
    this.messagingService.sendMessage(conversation.id, conversation.otherUserId, text).subscribe({
      next: message => {
        this.handleIncomingMessage(message);
        this.draftMessage = '';
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
      this.bindConversationChannel(conversation.id);
    });
  }

  private bindConversationChannel(conversationId: number): void {
    this.activeSubscription?.unsubscribe();
    this.messagingService.connect().then(() => {
      this.activeSubscription = this.messagingService.subscribeToConversation(
        conversationId,
        message => this.handleIncomingMessage(message)
      );
    }).catch(() => {
      // silent fail: fallback to REST only
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
    conversation.messages.push(mapped);
    conversation.lastMessage = mapped.text;
    conversation.lastTime = mapped.timestamp;

    if (this.selectedConversationId !== conversation.id) {
      conversation.unreadCount += 1;
    }
  }

  private toConversation(dto: ConversationDto): Conversation {
    const displayName = dto.otherDisplayName || dto.otherUsername || 'Unknown';
    return {
      id: dto.id,
      otherUserId: dto.otherUserId,
      name: displayName,
      lastMessage: dto.lastMessage || '',
      lastTime: dto.lastMessageAt ? this.formatTime(dto.lastMessageAt) : '',
      unreadCount: 0,
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
      fromMe
    };
  }

  private formatTime(value: string): string {
    const date = new Date(value);
    if (Number.isNaN(date.getTime())) {
      return '';
    }
    return date.toLocaleString();
  }
}
