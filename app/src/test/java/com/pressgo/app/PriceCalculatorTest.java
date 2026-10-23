package com.pressgo.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.pressgo.app.models.PriceBreakdown;
import com.pressgo.app.models.PrintOrder;
import com.pressgo.app.models.ShopPricing;
import com.pressgo.app.utils.PriceCalculator;

import org.junit.Before;
import org.junit.Test;

import java.util.List;

/**
 * Unit tests for print price calculation and page-range resolution.
 */
public class PriceCalculatorTest {

    private ShopPricing standardPricing;

    @Before
    public void setUp() {
        standardPricing = new ShopPricing();
        standardPricing.setShopId("shop_demo_01");
        standardPricing.setBwPageRate(0.10);
        standardPricing.setColorPageRate(0.50);
        standardPricing.setA4Multiplier(1.0);
        standardPricing.setA3Multiplier(1.5);
        standardPricing.setLetterMultiplier(1.0);
        standardPricing.setDuplexDiscount(0.02);
        standardPricing.setBindingCharge(2.50);
        standardPricing.setStaplingCharge(0.50);
        standardPricing.setPriorityCharge(3.00);
        standardPricing.setMinimumOrder(1.00);
    }

    @Test
    public void testPageRangeValidation() {
        assertTrue(PriceCalculator.validatePageRange("all", 10));
        assertTrue(PriceCalculator.validatePageRange("1-5", 10));
        assertTrue(PriceCalculator.validatePageRange("1,3,7-9", 10));
        assertTrue(PriceCalculator.validatePageRange("10", 10));

        // Out of bounds or invalid
        assertFalse(PriceCalculator.validatePageRange("1-15", 10));
        assertFalse(PriceCalculator.validatePageRange("0-5", 10));
        assertFalse(PriceCalculator.validatePageRange("abc", 10));
        assertFalse(PriceCalculator.validatePageRange(null, 10));
        assertFalse(PriceCalculator.validatePageRange("", 10));
    }

    @Test
    public void testParsePageRange() {
        List<Integer> pagesAll = PriceCalculator.parsePageRange("all", 5);
        assertEquals(5, pagesAll.size());
        assertEquals(Integer.valueOf(1), pagesAll.get(0));
        assertEquals(Integer.valueOf(5), pagesAll.get(4));

        List<Integer> pagesCustom = PriceCalculator.parsePageRange("1,3,5-7", 10);
        assertEquals(5, pagesCustom.size()); // 1, 3, 5, 6, 7
        assertTrue(pagesCustom.contains(1));
        assertTrue(pagesCustom.contains(3));
        assertTrue(pagesCustom.contains(5));
        assertTrue(pagesCustom.contains(6));
        assertTrue(pagesCustom.contains(7));
    }

    @Test
    public void testCalculateEstimatedPriceBwStandard() {
        PrintOrder order = new PrintOrder();
        order.setTotalPages(10);
        order.setCopies(1);
        order.setColor(false);
        order.setPaperSize("A4");
        order.setBothSides(false);
        order.setHasBinding(false);
        order.setHasStapling(false);
        order.setPriority(false);

        PriceBreakdown breakdown = PriceCalculator.calculateEstimatedPrice(order, standardPricing);
        assertNotNull(breakdown);
        assertEquals(10, breakdown.getTotalPages());
        assertEquals(10, breakdown.getBwPages());
        assertEquals(0, breakdown.getColorPages());
        // 10 pages * $0.10 * 1.0 (A4) * 1 copy = $1.00
        assertEquals(1.00, breakdown.getSubtotal(), 0.001);
        assertEquals(1.00, breakdown.getEstimatedTotal(), 0.001);
    }

    @Test
    public void testCalculateEstimatedPriceColorWithA3AndFinishing() {
        PrintOrder order = new PrintOrder();
        order.setTotalPages(4);
        order.setCopies(2);
        order.setColor(true);
        order.setPaperSize("A3");
        order.setBothSides(false);
        order.setHasBinding(true);
        order.setHasStapling(true);
        order.setPriority(true);

        PriceBreakdown breakdown = PriceCalculator.calculateEstimatedPrice(order, standardPricing);
        assertNotNull(breakdown);

        // 4 pages * 2 copies = 8 page prints
        // Color rate: $0.50
        // A3 multiplier: 1.5
        // Page cost = 4 * 0.50 * 1.5 * 2 = $6.00 subtotal
        // Binding charge: $2.50
        // Stapling charge: $0.50
        // Priority charge: $3.00
        // Total = 6.00 + 2.50 + 0.50 + 3.00 = $12.00
        assertEquals(6.00, breakdown.getSubtotal(), 0.001);
        assertEquals(12.00, breakdown.getEstimatedTotal(), 0.001);
    }

    @Test
    public void testCalculateEstimatedPriceDuplexDiscount() {
        PrintOrder order = new PrintOrder();
        order.setTotalPages(10);
        order.setCopies(1);
        order.setColor(false);
        order.setPaperSize("A4");
        order.setBothSides(true); // Duplex discount $0.02 * 10 = $0.20

        PriceBreakdown breakdown = PriceCalculator.calculateEstimatedPrice(order, standardPricing);
        assertNotNull(breakdown);
        assertEquals(1.00, breakdown.getSubtotal(), 0.001);
        assertEquals(0.20, breakdown.getDuplexDiscount(), 0.001);
        // $1.00 subtotal - $0.20 discount = $0.80, but minimumOrder is $1.00
        assertEquals(1.00, breakdown.getEstimatedTotal(), 0.001);
    }
}
