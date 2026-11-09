package com.pressgo.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.pressgo.app.models.ContactItem;
import com.pressgo.app.models.PickupAuthorization;
import com.pressgo.app.models.PrintOrder;
import com.pressgo.app.utils.ImplicitIntentUtils;

import org.junit.Test;

/**
 * Unit tests for Pickup Authorization data models, status management,
 * and message generation.
 */
public class PickupAuthorizationTest {

    @Test
    public void testPickupAuthorizationModelDefaults() {
        PickupAuthorization auth = new PickupAuthorization();
        assertEquals(PickupAuthorization.STATUS_PENDING_VERIFICATION, auth.getStatus());
        assertTrue(auth.isPending());
        assertFalse(auth.isVerified());
        assertFalse(auth.isRejected());
    }

    @Test
    public void testPickupAuthorizationParameterizedConstructor() {
        PickupAuthorization auth = new PickupAuthorization(
                "auth-123",
                "PG-20261109-1001",
                "cust-uid-456",
                "Rahim Chowdhury",
                "Karim Ullah",
                "+8801712345678",
                "Authorization message text",
                PickupAuthorization.STATUS_PENDING_VERIFICATION
        );

        assertEquals("auth-123", auth.getAuthorizationId());
        assertEquals("PG-20261109-1001", auth.getOrderId());
        assertEquals("cust-uid-456", auth.getCustomerUid());
        assertEquals("Rahim Chowdhury", auth.getCustomerName());
        assertEquals("Karim Ullah", auth.getAuthorizedContactName());
        assertEquals("+8801712345678", auth.getAuthorizedContactPhone());
        assertEquals("Authorization message text", auth.getAuthorizationMessage());
        assertTrue(auth.isPending());
    }

    @Test
    public void testStatusTransitions() {
        PickupAuthorization auth = new PickupAuthorization();
        auth.setStatus(PickupAuthorization.STATUS_VERIFIED);
        assertTrue(auth.isVerified());
        assertFalse(auth.isPending());

        auth.setStatus(PickupAuthorization.STATUS_REJECTED);
        assertTrue(auth.isRejected());
        assertFalse(auth.isVerified());

        auth.setStatus(PickupAuthorization.STATUS_REVOKED);
        assertTrue(auth.isRevoked());
    }

    @Test
    public void testBuildPickupAuthorizationMessage() {
        String message = ImplicitIntentUtils.buildPickupAuthorizationMessage(
                "Rahim Ahmed",
                "Tanvir Hasan",
                "PG-20261109",
                "Central Campus Printing Center"
        );

        assertNotNull(message);
        assertTrue("Must address the contact", message.contains("Hello Rahim Ahmed"));
        assertTrue("Must state order pickup authorization", message.contains("authorized you to collect my printing order from PressGo"));
        assertTrue("Must include exact Order ID", message.contains("Order ID: PG-20261109"));
        assertTrue("Must include pickup shop", message.contains("Central Campus Printing Center"));
        assertTrue("Must include customer name", message.contains("Customer: Tanvir Hasan"));
        assertTrue("Must state verification procedure", message.contains("follow the shop's verification procedure"));
        assertTrue("Must thank from customer", message.contains("Tanvir Hasan"));
    }

    @Test
    public void testBuildPickupAuthorizationMessageFallback() {
        String message = ImplicitIntentUtils.buildPickupAuthorizationMessage(
                null,
                "",
                null,
                ""
        );

        assertNotNull(message);
        assertTrue("Must fallback for contact name", message.contains("Hello Campus Peer"));
        assertTrue("Must fallback for customer name", message.contains("Customer: PressGo Customer"));
        assertTrue("Must fallback for order ID", message.contains("Order ID: N/A"));
        assertTrue("Must fallback for shop name", message.contains("Pickup Location: PressGo Campus Shop"));
    }

    @Test
    public void testContactItemPhoneHandling() {
        ContactItem contactWithPhone = new ContactItem(1L, "Alice Walker", "+880 1712-345678", null);
        assertTrue(contactWithPhone.hasPhoneNumber());
        assertEquals("+880 1712-345678", contactWithPhone.getPhoneNumber());

        ContactItem contactWithoutPhone = new ContactItem(2L, "Bob Builder", "", null);
        assertFalse(contactWithoutPhone.hasPhoneNumber());

        ContactItem contactWithNullPhone = new ContactItem(3L, "Charlie Brown", null, null);
        assertFalse(contactWithNullPhone.hasPhoneNumber());
    }

    @Test
    public void testContactItemInitials() {
        ContactItem item1 = new ContactItem(1L, "John Doe", "123", null);
        assertEquals("JD", item1.getInitials());

        ContactItem item2 = new ContactItem(2L, "Alice", "123", null);
        assertEquals("AL", item2.getInitials());

        ContactItem item3 = new ContactItem(3L, "", "123", null);
        assertEquals("UC", item3.getInitials());
    }
}
