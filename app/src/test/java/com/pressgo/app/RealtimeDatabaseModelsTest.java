package com.pressgo.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.pressgo.app.models.LiveNotification;
import com.pressgo.app.models.LiveOrderStatus;
import com.pressgo.app.models.LivePrinterStatus;
import com.pressgo.app.models.LiveShopStatus;
import com.pressgo.app.models.Notification;
import com.pressgo.app.models.Printer;
import com.pressgo.app.repositories.RealtimeDatabaseRepository;

import org.junit.Test;

import java.util.Map;

/**
 * Unit tests for Firebase Realtime Database models and repository contracts.
 */
public class RealtimeDatabaseModelsTest {

    @Test
    public void testLiveOrderStatusMapping() {
        LiveOrderStatus orderStatus = new LiveOrderStatus(
                "PG-20261104-1234",
                "ACCEPTED",
                "user_student_1",
                "shop_library_hub",
                "Thesis binding requested",
                "token_abc_123",
                System.currentTimeMillis(),
                15
        );

        assertEquals("PG-20261104-1234", orderStatus.getOrderId());
        assertEquals("ACCEPTED", orderStatus.getOrderStatus());
        assertEquals("user_student_1", orderStatus.getCustomerId());
        assertEquals("shop_library_hub", orderStatus.getShopId());
        assertEquals("Thesis binding requested", orderStatus.getStaffNotes());
        assertEquals("token_abc_123", orderStatus.getPickupToken());
        assertEquals(Integer.valueOf(15), orderStatus.getEstimatedWaitMinutes());

        Map<String, Object> map = orderStatus.toMap();
        assertEquals("PG-20261104-1234", map.get("orderId"));
        assertEquals("ACCEPTED", map.get("orderStatus"));
        assertEquals("ACCEPTED", map.get("status"));
        assertEquals("user_student_1", map.get("customerId"));
        assertEquals("shop_library_hub", map.get("shopId"));
        assertEquals(15, map.get("estimatedWaitMinutes"));
        assertTrue(map.containsKey("updatedAt"));
    }

    @Test
    public void testLivePrinterStatusMapping() {
        LivePrinterStatus printerStatus = new LivePrinterStatus(
                "printer_hp_01",
                "shop_library_hub",
                "HP LaserJet Enterprise",
                Printer.STATUS_BUSY,
                80,
                65,
                90,
                "PG-20261104-1234",
                System.currentTimeMillis()
        );

        assertEquals("printer_hp_01", printerStatus.getPrinterId());
        assertEquals("HP LaserJet Enterprise", printerStatus.getName());
        assertEquals(Printer.STATUS_BUSY, printerStatus.getStatus());
        assertEquals(80, printerStatus.getPaperLevel());
        assertEquals(65, printerStatus.getInkLevel());
        assertEquals(90, printerStatus.getTonerLevel());
        assertEquals("PG-20261104-1234", printerStatus.getCurrentJobId());

        Map<String, Object> map = printerStatus.toMap();
        assertEquals("printer_hp_01", map.get("printerId"));
        assertEquals("shop_library_hub", map.get("shopId"));
        assertEquals(Printer.STATUS_BUSY, map.get("status"));
        assertEquals(80, map.get("paperLevel"));
        assertEquals(65, map.get("inkLevel"));
        assertEquals(90, map.get("tonerLevel"));
        assertTrue(map.containsKey("updatedAt"));
    }

    @Test
    public void testLiveShopStatusMapping() {
        LiveShopStatus shopStatus = new LiveShopStatus(
                "shop_campus_central",
                true,
                3,
                10,
                System.currentTimeMillis()
        );

        assertEquals("shop_campus_central", shopStatus.getShopId());
        assertTrue(shopStatus.isOpen());
        assertEquals(3, shopStatus.getQueueLength());
        assertEquals(10, shopStatus.getEstimatedWaitMinutes());

        Map<String, Object> map = shopStatus.toMap();
        assertEquals("shop_campus_central", map.get("shopId"));
        assertEquals(true, map.get("isOpen"));
        assertEquals(true, map.get("open"));
        assertEquals(3, map.get("queueLength"));
        assertEquals(10, map.get("estimatedWaitMinutes"));
        assertTrue(map.containsKey("updatedAt"));

        shopStatus.setIsOpen(false);
        assertFalse(shopStatus.isOpen());
        assertFalse(shopStatus.getOpen());
    }

    @Test
    public void testLiveNotificationMapping() {
        LiveNotification notification = new LiveNotification(
                "notif_1001",
                "user_student_1",
                "PG-20261104-1234",
                "Printout Ready! 📄",
                "Your print order is ready for pickup.",
                Notification.TYPE_READY_FOR_PICKUP,
                false,
                System.currentTimeMillis()
        );

        assertEquals("notif_1001", notification.getId());
        assertEquals("user_student_1", notification.getUserId());
        assertEquals("PG-20261104-1234", notification.getOrderId());
        assertEquals("Printout Ready! 📄", notification.getTitle());
        assertEquals(Notification.TYPE_READY_FOR_PICKUP, notification.getType());
        assertEquals(false, notification.isRead());

        Map<String, Object> map = notification.toMap();
        assertEquals("notif_1001", map.get("id"));
        assertEquals("user_student_1", map.get("userId"));
        assertEquals("PG-20261104-1234", map.get("orderId"));
        assertEquals("Printout Ready! 📄", map.get("title"));
        assertEquals(false, map.get("read"));
        assertTrue(map.containsKey("timestamp"));
    }

    @Test
    public void testRealtimeDatabaseRepositoryPaths() {
        assertEquals("liveOrders", RealtimeDatabaseRepository.PATH_LIVE_ORDERS);
        assertEquals("printerStatus", RealtimeDatabaseRepository.PATH_PRINTER_STATUS);
        assertEquals("shopStatus", RealtimeDatabaseRepository.PATH_SHOP_STATUS);
        assertEquals("liveNotifications", RealtimeDatabaseRepository.PATH_LIVE_NOTIFS);
    }
}
