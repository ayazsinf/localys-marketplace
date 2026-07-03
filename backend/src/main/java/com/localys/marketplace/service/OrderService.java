package com.localys.marketplace.service;

import com.localys.marketplace.dto.OrderCreateRequest;
import com.localys.marketplace.dto.OrderResponse;
import com.localys.marketplace.model.Address;
import com.localys.marketplace.model.Cart;
import com.localys.marketplace.model.CartItem;
import com.localys.marketplace.model.Order;
import com.localys.marketplace.model.OrderItem;
import com.localys.marketplace.model.Product;
import com.localys.marketplace.model.ProductRemovalEvent;
import com.localys.marketplace.model.UserEntity;
import com.localys.marketplace.model.enums.CartStatus;
import com.localys.marketplace.model.enums.ListingRemovalReason;
import com.localys.marketplace.model.enums.OrderStatus;
import com.localys.marketplace.model.enums.ModerationStatus;
import com.localys.marketplace.repository.AddressRepository;
import com.localys.marketplace.repository.CartItemRepository;
import com.localys.marketplace.repository.CartRepository;
import com.localys.marketplace.repository.OrderItemRepository;
import com.localys.marketplace.repository.OrderRepository;
import com.localys.marketplace.repository.ProductRemovalEventRepository;
import com.localys.marketplace.repository.UserRepository;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

@Service
public class OrderService {

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final UserRepository userRepository;
    private final AddressRepository addressRepository;
    private final ProductRemovalEventRepository productRemovalEventRepository;

    public OrderService(
            OrderRepository orderRepository,
            OrderItemRepository orderItemRepository,
            CartRepository cartRepository,
            CartItemRepository cartItemRepository,
            UserRepository userRepository,
            AddressRepository addressRepository,
            ProductRemovalEventRepository productRemovalEventRepository
    ) {
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.cartRepository = cartRepository;
        this.cartItemRepository = cartItemRepository;
        this.userRepository = userRepository;
        this.addressRepository = addressRepository;
        this.productRemovalEventRepository = productRemovalEventRepository;
    }

    @Transactional
    public OrderResponse createOrder(Long userId, OrderCreateRequest request) {
        if (request.productIds() == null || request.productIds().isEmpty()) {
            throw new IllegalArgumentException("No items selected");
        }
        if (request.addressId() == null) {
            throw new IllegalArgumentException("Address is required");
        }
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        Address address = addressRepository.findByIdAndUser_Id(request.addressId(), userId)
                .orElseThrow(() -> new IllegalArgumentException("Address not found"));

        Cart cart = cartRepository.findByUserIdAndStatus(userId, CartStatus.ACTIVE)
                .orElseThrow(() -> new IllegalArgumentException("Cart not found"));

        List<CartItem> selectedItems = cart.getItems().stream()
                .filter(item -> request.productIds().contains(item.getProduct().getId()))
                .toList();
        if (selectedItems.isEmpty()) {
            throw new IllegalArgumentException("No items selected");
        }

        Order order = new Order();
        order.setUser(user);
        order.setStatus(OrderStatus.PAYMENT_PENDING);
        order.setShippingAddress(address);
        order.setBillingAddress(address);
        order.setShippingMethod(request.shippingMethod());
        order.setPaymentMethod(request.paymentMethod());
        BigDecimal shippingPrice = request.shippingPrice() != null ? request.shippingPrice() : BigDecimal.ZERO;
        order.setShippingPrice(shippingPrice);

        BigDecimal subtotal = BigDecimal.ZERO;
        for (CartItem cartItem : selectedItems) {
            Product product = cartItem.getProduct();
            if (!product.isActive() || product.getModerationStatus() != ModerationStatus.APPROVED) {
                throw new IllegalArgumentException("Product not found");
            }
            if (cartItem.getQuantity() > product.getStockQty()) {
                throw new IllegalArgumentException("Product out of stock");
            }
            OrderItem item = new OrderItem();
            item.setOrder(order);
            item.setProduct(product);
            item.setVendor(product.getVendor());
            item.setProductNameSnapshot(product.getName());
            item.setUnitPriceSnapshot(cartItem.getUnitPriceSnapshot());
            item.setQuantity(cartItem.getQuantity());
            BigDecimal lineTotal = cartItem.getUnitPriceSnapshot()
                    .multiply(BigDecimal.valueOf(cartItem.getQuantity()));
            item.setLineTotal(lineTotal);
            item.setCurrency(cartItem.getCurrency());
            order.getItems().add(item);
            subtotal = subtotal.add(lineTotal);
            int remainingStock = product.getStockQty() - cartItem.getQuantity();
            product.setStockQty(Math.max(remainingStock, 0));
            if (remainingStock <= 0) {
                product.setActive(false);
                product.setRemovalReason(ListingRemovalReason.SOLD_ON_LOCALYS);
                product.setRemovalNote(null);
                product.setRemovedAt(OffsetDateTime.now());
                ProductRemovalEvent event = new ProductRemovalEvent();
                event.setProduct(product);
                event.setReason(ListingRemovalReason.SOLD_ON_LOCALYS);
                event.setActorUser(user);
                event.setOccurredAt(product.getRemovedAt());
                productRemovalEventRepository.save(event);
            }
        }

        order.setTotalAmount(subtotal.add(shippingPrice));
        order.setCurrency(selectedItems.get(0).getCurrency());
        Order saved = orderRepository.save(order);
        orderItemRepository.saveAll(saved.getItems());

        selectedItems.forEach(item -> {
            cartItemRepository.delete(item);
            cart.getItems().remove(item);
        });

        return toResponse(saved, subtotal);
    }

    private OrderResponse toResponse(Order order, BigDecimal subtotal) {
        List<OrderResponse.OrderItemResponse> items = order.getItems().stream()
                .map(item -> new OrderResponse.OrderItemResponse(
                        item.getProduct().getId(),
                        item.getProductNameSnapshot(),
                        item.getQuantity(),
                        item.getLineTotal()
                ))
                .toList();
        return new OrderResponse(
                order.getId(),
                order.getStatus().name(),
                subtotal,
                order.getShippingPrice(),
                order.getTotalAmount(),
                order.getCurrency(),
                order.getCreatedAt(),
                items
        );
    }
}
