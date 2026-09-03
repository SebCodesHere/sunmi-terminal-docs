package com.example.sunmireceipt;

import java.io.Serializable;

public class ProductItem implements Serializable {
    public String name;
    public double price;
    public String barcode;
    public boolean isPinned;

    public ProductItem(String name, double price) {
        this.name = name;
        this.price = price;
        this.isPinned = false;
    }

    public ProductItem(String name, double price, String barcode) {
        this.name = name;
        this.price = price;
        this.barcode = barcode;
        this.isPinned = false;
    }
}
