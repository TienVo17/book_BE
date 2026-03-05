package com.example.book_be.services;

import com.example.book_be.dao.ChiTietDonHangRepository;
import com.example.book_be.dao.DonHangRepository;
import com.example.book_be.dao.NguoiDungRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Implementation of ThongKeService that aggregates admin dashboard statistics
 * from orders, revenue, and user counts.
 */
@Service
public class ThongKeServiceImpl implements ThongKeService {

    @Autowired
    private DonHangRepository donHangRepository;

    @Autowired
    private ChiTietDonHangRepository chiTietDonHangRepository;

    @Autowired
    private NguoiDungRepository nguoiDungRepository;

    @Override
    public Map<String, Object> getThongKe() {
        Map<String, Object> result = new HashMap<>();

        // Order and revenue aggregates
        result.put("totalOrders", donHangRepository.count());
        result.put("totalRevenue", donHangRepository.sumDoanhThu());
        result.put("todayOrders", donHangRepository.countDonHangHomNay());
        result.put("todayRevenue", donHangRepository.sumDoanhThuHomNay());
        result.put("totalUsers", nguoiDungRepository.count());

        // Pending orders: trangThaiGiaoHang = 0 means chua giao
        result.put("pendingOrders", donHangRepository.countByTrangThaiGiaoHang(0));

        // Top 5 best-selling books
        List<Object[]> topBanChayRaw = chiTietDonHangRepository.findTopBanChay(PageRequest.of(0, 5));
        List<Map<String, Object>> topBanChay = new ArrayList<>();
        for (Object[] row : topBanChayRaw) {
            Map<String, Object> item = new HashMap<>();
            item.put("maSach", row[0]);
            item.put("tenSach", row[1]);
            item.put("tongBan", row[2]);
            topBanChay.add(item);
        }
        result.put("topBanChay", topBanChay);

        return result;
    }
}
