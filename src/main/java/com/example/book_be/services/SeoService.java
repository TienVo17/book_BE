package com.example.book_be.services;

import java.util.Map;

public interface SeoService {
    Map<String, Object> getMetaTags(int maSach);
    String generateSitemap();
}
