package com.pressgo.app.utils;

import android.util.Log;
import android.util.Xml;

import com.pressgo.app.models.PrintOrder;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlSerializer;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;

/**
 * Dedicated XML Parser & Serializer for PressGo using Android's native XmlPullParser.
 * Parses and generates print job manifests, shop specifications, and export configs.
 * (Week 11: XML Parsing).
 */
public final class XmlDocumentParser {

    private static final String TAG = "XmlDocumentParser";

    private XmlDocumentParser() {}

    /**
     * Parses an XML string into a PrintOrder domain model using XmlPullParser.
     *
     * @param xmlString XML text to parse
     * @return Deserialized PrintOrder model or null on parse error
     */
    public static PrintOrder parsePrintJobXml(String xmlString) {
        if (xmlString == null || xmlString.trim().isEmpty()) {
            return null;
        }
        try (InputStream is = new ByteArrayInputStream(xmlString.getBytes(StandardCharsets.UTF_8))) {
            return parsePrintJobXml(is);
        } catch (Exception e) {
            Log.e(TAG, "Error parsing XML string", e);
            return null;
        }
    }

    /**
     * Parses an XML InputStream using Android's native XmlPullParser.
     */
    public static PrintOrder parsePrintJobXml(InputStream inputStream) throws XmlPullParserException, IOException {
        if (inputStream == null) return null;

        XmlPullParser parser = Xml.newPullParser();
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false);
        parser.setInput(inputStream, "UTF-8");

        PrintOrder order = new PrintOrder();
        int eventType = parser.getEventType();
        String currentTag = null;

        while (eventType != XmlPullParser.END_DOCUMENT) {
            switch (eventType) {
                case XmlPullParser.START_TAG:
                    currentTag = parser.getName();
                    if ("printJob".equalsIgnoreCase(currentTag)) {
                        String idAttr = parser.getAttributeValue(null, "id");
                        if (idAttr != null) {
                            order.setId(idAttr);
                            order.setOrderId(idAttr);
                        }
                    }
                    break;

                case XmlPullParser.TEXT:
                    if (currentTag != null && !parser.isWhitespace()) {
                        String text = parser.getText().trim();
                        applyTagValue(order, currentTag, text);
                    }
                    break;

                case XmlPullParser.END_TAG:
                    currentTag = null;
                    break;
            }
            eventType = parser.next();
        }

        return order;
    }

    private static void applyTagValue(PrintOrder order, String tag, String value) {
        if (order == null || tag == null || value == null) return;
        try {
            switch (tag.toLowerCase()) {
                case "id":
                case "orderid":
                    order.setId(value);
                    order.setOrderId(value);
                    break;
                case "shopid":
                    order.setShopId(value);
                    break;
                case "customerid":
                    order.setCustomerId(value);
                    break;
                case "customername":
                    order.setCustomerName(value);
                    break;
                case "status":
                case "orderstatus":
                    order.setOrderStatus(value);
                    break;
                case "copies":
                    order.setCopies(Integer.parseInt(value));
                    break;
                case "color":
                case "iscolor":
                    order.setColor(Boolean.parseBoolean(value));
                    break;
                case "papersize":
                    order.setPaperSize(value);
                    break;
                case "bothsides":
                case "isbothsides":
                    order.setBothSides(Boolean.parseBoolean(value));
                    break;
                case "binding":
                case "hasbinding":
                    order.setHasBinding(Boolean.parseBoolean(value));
                    break;
                case "stapling":
                case "hasstapling":
                    order.setHasStapling(Boolean.parseBoolean(value));
                    break;
                case "pagerange":
                    order.setPageRange(value);
                    break;
                case "totalpages":
                    order.setTotalPages(Integer.parseInt(value));
                    break;
                case "estimatedprice":
                case "price":
                case "totalamount":
                    order.setEstimatedPrice(Double.parseDouble(value));
                    break;
            }
        } catch (Exception e) {
            Log.w(TAG, "Error applying XML value for tag <" + tag + ">: " + value, e);
        }
    }

    /**
     * Serializes a PrintOrder model into standard formatted XML using XmlSerializer.
     */
    public static String serializePrintJobToXml(PrintOrder order) {
        if (order == null) return "";
        try {
            XmlSerializer serializer = Xml.newSerializer();
            StringWriter writer = new StringWriter();
            serializer.setOutput(writer);
            serializer.startDocument("UTF-8", true);

            serializer.startTag(null, "printJob");
            if (order.getId() != null) {
                serializer.attribute(null, "id", order.getId());
            }

            writeElement(serializer, "orderId", order.getId());
            writeElement(serializer, "shopId", order.getShopId());
            writeElement(serializer, "customerId", order.getCustomerId());
            writeElement(serializer, "status", order.getOrderStatus());
            writeElement(serializer, "copies", String.valueOf(order.getCopies()));
            writeElement(serializer, "isColor", String.valueOf(order.isColor()));
            writeElement(serializer, "paperSize", order.getPaperSize());
            writeElement(serializer, "isBothSides", String.valueOf(order.isBothSides()));
            writeElement(serializer, "hasBinding", String.valueOf(order.hasBinding()));
            writeElement(serializer, "hasStapling", String.valueOf(order.hasStapling()));
            writeElement(serializer, "pageRange", order.getPageRange());
            writeElement(serializer, "totalPages", String.valueOf(order.getTotalPages()));
            writeElement(serializer, "estimatedPrice", String.valueOf(order.getEstimatedPrice()));

            serializer.endTag(null, "printJob");
            serializer.endDocument();
            return writer.toString();
        } catch (Exception e) {
            Log.e(TAG, "Error serializing PrintOrder to XML", e);
            return "";
        }
    }

    private static void writeElement(XmlSerializer serializer, String tag, String value) throws IOException {
        if (value == null) value = "";
        serializer.startTag(null, tag);
        serializer.text(value);
        serializer.endTag(null, tag);
    }
}
