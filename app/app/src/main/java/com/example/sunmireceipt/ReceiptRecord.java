package com.example.sunmireceipt;

import java.io.Serializable;
import java.util.List;

public class ReceiptRecord implements Serializable {
    public String receiptNo;
    public long timestamp;
    public double total;
    public List<ProductItem> items;
    public double subtotal;
    public double discount;
    public String note;

    public ReceiptRecord(String receiptNo, long timestamp, double subtotal, double discount, double total, List<ProductItem> items, String note) {
        this.receiptNo = receiptNo;
        this.timestamp = timestamp;
        this.subtotal = subtotal;
        this.discount = discount;
        this.total = total;
        this.items = items;
        this.note = note;
    }
}
