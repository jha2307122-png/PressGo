package com.pressgo.app;

import static org.junit.Assert.*;

import com.pressgo.app.models.ContactItem;

import org.junit.Test;

/**
 * Unit tests for ContactItem model used with Contacts Content Provider.
 */
public class ContactItemTest {

    @Test
    public void testContactItemProperties() {
        ContactItem item = new ContactItem(101L, "Alice Walker", "+8801700000000", "content://contacts/101");
        assertEquals(101L, item.getId());
        assertEquals("Alice Walker", item.getName());
        assertEquals("+8801700000000", item.getPhoneNumber());
        assertEquals("content://contacts/101", item.getPhotoUri());
        assertTrue(item.hasPhoneNumber());
    }

    @Test
    public void testContactInitialsTwoWords() {
        ContactItem item = new ContactItem(1L, "Alice Walker", "+880123", null);
        assertEquals("AW", item.getInitials());
    }

    @Test
    public void testContactInitialsSingleWord() {
        ContactItem item = new ContactItem(2L, "Bob", "+880123", null);
        assertEquals("BO", item.getInitials());
    }

    @Test
    public void testContactInitialsEmpty() {
        ContactItem item = new ContactItem(3L, "", null, null);
        assertEquals("UC", item.getInitials()); // fallback to "Unnamed Contact" -> "UC"
    }

    @Test
    public void testEmptyPhoneHandling() {
        ContactItem item = new ContactItem(4L, "Charlie", "", null);
        assertFalse(item.hasPhoneNumber());
        item.setPhoneNumber(null);
        assertFalse(item.hasPhoneNumber());
    }
}
