package com.example.sunmireceipt;

import android.content.Context;
import android.os.RemoteException;
import com.sunmi.peripheral.printer.InnerPrinterCallback;
import com.sunmi.peripheral.printer.InnerPrinterManager;
import com.sunmi.peripheral.printer.SunmiPrinterService;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class PrinterHelper {
    public interface PrinterStatusListener {
        void onStatusChanged(String status, int colorResId);
    }

    public interface PrintCallback {
        void onPrintSuccess();
        void onPrintFailed(String error);
    }

    private SunmiPrinterService printerService;
    private PrinterStatusListener listener;
    private Context context;

    public PrinterHelper(Context context) {
        this.context = context.getApplicationContext();
    }

    public void setListener(PrinterStatusListener listener) {
        this.listener = listener;
    }

    public void printReceipt(List<ProductItem> items, double subtotal, double discount, double total, 
                           String storeName, String address, String footer,
                           String receiptNo, String note, PrintCallback callback) {
        if (printerService == null) {
            callback.onPrintFailed("Printer offline");
            return;
        }

        try {
            int status = printerService.updatePrinterState();
            if (status != 1) {
                callback.onPrintFailed(getStatusDescription(status));
                return;
            }

            printerService.printerInit(null);

            // Branding Header
            printerService.setAlignment(1, null);
            printerService.setFontSize(24.0f, null);
            printerService.sendRAWData(new byte[]{0x1B, 0x45, 0x01}, null); // Bold
            printerService.printText("Terminal by Seb\n", null);
            printerService.sendRAWData(new byte[]{0x1B, 0x45, 0x00}, null);
            printerService.lineWrap(1, null);

            // Store Header
            printerService.setAlignment(1, null);
            printerService.setFontSize(32.0f, null);
            printerService.sendRAWData(new byte[]{0x1B, 0x45, 0x01}, null); // Bold
            printerService.printText((storeName != null && !storeName.isEmpty() ? storeName : "SNM POS") + "\n", null);
            printerService.sendRAWData(new byte[]{0x1B, 0x45, 0x00}, null);
            
            printerService.setFontSize(22.0f, null);
            if (address != null && !address.isEmpty()) printerService.printText(address + "\n", null);
            
            printerService.lineWrap(1, null);
            SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault());
            printerService.printText("Receipt #" + receiptNo + "\n", null);
            printerService.printText(sdf.format(new Date()) + "\n", null);
            
            printerService.printText("--------------------------------\n", null);

            // Items
            printerService.setAlignment(0, null);
            for (ProductItem item : items) {
                String priceStr = String.format(Locale.US, "€%.2f", item.price);
                printerService.printText(formatLine(item.name, priceStr, 31) + "\n", null);
            }

            printerService.printText("--------------------------------\n", null);

            // Financials
            if (discount > 0) {
                printerService.printText(formatLine("SUBTOTAL", String.format(Locale.US, "€%.2f", subtotal), 31) + "\n", null);
                printerService.printText(formatLine("DISCOUNT", String.format(Locale.US, "-€%.2f", discount), 31) + "\n", null);
                printerService.printText("--------------------------------\n", null);
            }

            // Total
            printerService.setFontSize(32.0f, null);
            printerService.sendRAWData(new byte[]{0x1B, 0x45, 0x01}, null);
            String totalStr = String.format(Locale.US, "€%.2f", total);
            printerService.printText(formatLine("TOTAL", totalStr, 31) + "\n", null);
            printerService.sendRAWData(new byte[]{0x1B, 0x45, 0x00}, null);
            
            printerService.setFontSize(24.0f, null);
            printerService.printText("--------------------------------\n", null);

            // Note
            if (note != null && !note.isEmpty()) {
                printerService.setAlignment(0, null);
                printerService.printText("NOTE: " + note + "\n", null);
                printerService.printText("--------------------------------\n", null);
            }

            // Footer
            printerService.setAlignment(1, null);
            printerService.setFontSize(24.0f, null);
            printerService.printText((footer != null && !footer.isEmpty() ? footer : "Thanks for visiting!") + "\n", null);

            // QR Code
            printerService.lineWrap(1, null);
            printerService.setAlignment(1, null);
            printerService.printQRCode(receiptNo, 8, 1, null);

            // Branding Footer
            printerService.lineWrap(1, null);
            printerService.printText("- Made with love by Seb <3 -\n", null);

            printerService.lineWrap(4, null);
            callback.onPrintSuccess();
        } catch (Exception e) {
            callback.onPrintFailed("Print Error");
        }
    }

    private String formatLine(String left, String right, int width) {
        if (left == null) left = "";
        if (right == null) right = "";
        int spaceCount = width - left.length() - right.length();
        if (spaceCount < 1) {
            left = left.substring(0, Math.max(0, width - right.length() - 4)) + "...";
            spaceCount = width - left.length() - right.length();
        }
        StringBuilder sb = new StringBuilder(left);
        for (int i = 0; i < spaceCount; i++) sb.append(" ");
        sb.append(right);
        return sb.toString();
    }

    public void connect() {
        try {
            InnerPrinterManager.getInstance().bindService(context, new InnerPrinterCallback() {
                @Override
                protected void onConnected(SunmiPrinterService service) {
                    printerService = service;
                    updateStatus();
                }

                @Override
                protected void onDisconnected() {
                    printerService = null;
                    if (listener != null) listener.onStatusChanged("OFFLINE", R.color.error);
                }
            });
        } catch (Exception e) {
            if (listener != null) listener.onStatusChanged("OFFLINE", R.color.error);
        }
    }

    public void updateStatus() {
        if (printerService == null) {
            if (listener != null) listener.onStatusChanged("OFFLINE", R.color.error);
            return;
        }
        try {
            int status = printerService.updatePrinterState();
            String desc = getStatusDescription(status);
            int color = R.color.primary;
            if (status == 4) color = R.color.accent_orange;
            else if (status != 1) color = R.color.error;
            
            if (listener != null) listener.onStatusChanged(desc.toUpperCase(), color);
        } catch (RemoteException e) {
            if (listener != null) listener.onStatusChanged("ERROR", R.color.error);
        }
    }

    private String getStatusDescription(int status) {
        switch (status) {
            case 1: return "Ready";
            case 2: return "Preparing";
            case 3: return "Abnormal";
            case 4: return "Paper Low";
            case 5: return "Overheated";
            case 6: return "Cover Open";
            default: return "Error";
        }
    }

    public void printCustomText(String text, int alignment, boolean isBold, float fontSize, PrintCallback callback) {
        if (printerService == null) {
            callback.onPrintFailed("Offline");
            return;
        }
        try {
            printerService.printerInit(null);
            printerService.setAlignment(alignment, null);
            if (isBold) {
                printerService.sendRAWData(new byte[]{0x1B, 0x45, 0x01}, null);
            } else {
                printerService.sendRAWData(new byte[]{0x1B, 0x45, 0x00}, null);
            }
            printerService.setFontSize(fontSize, null);
            printerService.printText(text + "\n", null);
            printerService.sendRAWData(new byte[]{0x1B, 0x45, 0x00}, null);
            printerService.lineWrap(3, null);
            callback.onPrintSuccess();
        } catch (Exception e) {
            callback.onPrintFailed("Error");
        }
    }

    public void printImage(android.graphics.Bitmap bitmap, int alignment, PrintCallback callback) {
        if (printerService == null) {
            callback.onPrintFailed("Offline");
            return;
        }
        try {
            printerService.printerInit(null);
            printerService.setAlignment(alignment, null);
            
            int width = bitmap.getWidth();
            int height = bitmap.getHeight();
            int newWidth = 384;
            int newHeight = (int) ((double) height * newWidth / width);
            android.graphics.Bitmap resized = android.graphics.Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true);
            
            printerService.printBitmap(resized, null);
            printerService.lineWrap(3, null);
            if (resized != bitmap) resized.recycle();
            callback.onPrintSuccess();
        } catch (Exception e) {
            callback.onPrintFailed("Error");
        }
    }

    public void printBoth(String text, android.graphics.Bitmap bitmap, int alignment, float fontSize, boolean isBold, PrintCallback callback) {
        if (printerService == null) {
            callback.onPrintFailed("Offline");
            return;
        }
        try {
            printerService.printerInit(null);
            printerService.setAlignment(alignment, null);
            
            if (bitmap != null) {
                int width = bitmap.getWidth();
                int height = bitmap.getHeight();
                int newWidth = 384;
                int newHeight = (int) ((double) height * newWidth / width);
                android.graphics.Bitmap resized = android.graphics.Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true);
                printerService.printBitmap(resized, null);
                printerService.lineWrap(1, null);
                if (resized != bitmap) resized.recycle();
            }
            
            if (text != null && !text.isEmpty()) {
                if (isBold) printerService.sendRAWData(new byte[]{0x1B, 0x45, 0x01}, null);
                printerService.setFontSize(fontSize, null);
                printerService.printText(text + "\n", null);
                printerService.sendRAWData(new byte[]{0x1B, 0x45, 0x00}, null);
            }
            
            printerService.lineWrap(3, null);
            callback.onPrintSuccess();
        } catch (Exception e) {
            callback.onPrintFailed("Error");
        }
    }

    public void disconnect() {
        try {
            InnerPrinterManager.getInstance().unBindService(context, null);
        } catch (Exception ignored) {}
    }
    
    public void printTest(String storeName, PrintCallback callback) {
        if (printerService == null) {
            callback.onPrintFailed("Offline");
            return;
        }
        try {
            printerService.printerInit(null);
            printerService.setAlignment(1, null);
            printerService.printText("SNM POS\nPRINTER TEST\n\n✓ Ready\n\n", null);
            printerService.lineWrap(3, null);
            callback.onPrintSuccess();
        } catch (RemoteException e) {
            callback.onPrintFailed("Failed");
        }
    }
}
