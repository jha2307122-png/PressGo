package com.pressgo.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.pressgo.app.utils.ValidationUtils;

import org.junit.Test;

/**
 * Unit tests for input validation utility methods.
 */
public class ValidationUtilsTest {

    @Test
    public void testEmailValidation() {
        assertTrue(ValidationUtils.isValidEmail("student@university.edu"));
        assertTrue(ValidationUtils.isValidEmail("john.doe+pressgo@campus.ac.in"));
        assertTrue(ValidationUtils.isValidEmail("admin123@pressgo.com"));

        assertFalse(ValidationUtils.isValidEmail(null));
        assertFalse(ValidationUtils.isValidEmail(""));
        assertFalse(ValidationUtils.isValidEmail("   "));
        assertFalse(ValidationUtils.isValidEmail("plainaddress"));
        assertFalse(ValidationUtils.isValidEmail("@missingusername.com"));
        assertFalse(ValidationUtils.isValidEmail("username@.com"));
    }

    @Test
    public void testPasswordValidation() {
        assertTrue(ValidationUtils.isValidPassword("password123"));
        assertTrue(ValidationUtils.isValidPassword("12345678"));
        assertTrue(ValidationUtils.isValidPassword("SecureP@ssw0rd!"));

        assertFalse(ValidationUtils.isValidPassword(null));
        assertFalse(ValidationUtils.isValidPassword(""));
        assertFalse(ValidationUtils.isValidPassword("short7"));
    }

    @Test
    public void testPhoneValidation() {
        assertTrue(ValidationUtils.isValidPhone("+1234567890"));
        assertTrue(ValidationUtils.isValidPhone("9876543210"));
        assertTrue(ValidationUtils.isValidPhone("+9779812345678"));

        assertFalse(ValidationUtils.isValidPhone(null));
        assertFalse(ValidationUtils.isValidPhone(""));
        assertFalse(ValidationUtils.isValidPhone("123"));
        assertFalse(ValidationUtils.isValidPhone("phone_number"));
    }

    @Test
    public void testNameValidation() {
        assertTrue(ValidationUtils.isValidName("John Doe"));
        assertTrue(ValidationUtils.isValidName("Al"));
        assertTrue(ValidationUtils.isValidName("Professor Charles Xavier"));

        assertFalse(ValidationUtils.isValidName(null));
        assertFalse(ValidationUtils.isValidName(""));
        assertFalse(ValidationUtils.isValidName(" "));
        assertFalse(ValidationUtils.isValidName("A"));
    }

    @Test
    public void testCopiesValidation() {
        assertTrue(ValidationUtils.isValidCopies(1));
        assertTrue(ValidationUtils.isValidCopies(50));
        assertTrue(ValidationUtils.isValidCopies(999));

        assertFalse(ValidationUtils.isValidCopies(0));
        assertFalse(ValidationUtils.isValidCopies(-5));
        assertFalse(ValidationUtils.isValidCopies(1000));
    }

    @Test
    public void testPageRangeValidation() {
        assertTrue(ValidationUtils.isValidPageRange("all"));
        assertTrue(ValidationUtils.isValidPageRange("ALL"));
        assertTrue(ValidationUtils.isValidPageRange("1-5"));
        assertTrue(ValidationUtils.isValidPageRange("1,3,5"));
        assertTrue(ValidationUtils.isValidPageRange("1-4,7,9-12"));

        assertFalse(ValidationUtils.isValidPageRange(null));
        assertFalse(ValidationUtils.isValidPageRange(""));
        assertFalse(ValidationUtils.isValidPageRange("first-to-last"));
        assertFalse(ValidationUtils.isValidPageRange("1--5"));
    }

    @Test
    public void testBudgetValidation() {
        assertTrue(ValidationUtils.isValidBudget(10.0));
        assertTrue(ValidationUtils.isValidBudget(0.50));
        assertTrue(ValidationUtils.isValidBudget(500.0));

        assertFalse(ValidationUtils.isValidBudget(0.0));
        assertFalse(ValidationUtils.isValidBudget(-10.0));
        assertFalse(ValidationUtils.isValidBudget(Double.POSITIVE_INFINITY));
        assertFalse(ValidationUtils.isValidBudget(Double.NaN));
    }
}
