package com.localys.marketplace.repository;

/** A per-listing count returned by grouped queries (favorites, conversations, offers). */
public interface ProductCount {
    Long getProductId();

    long getCount();
}
