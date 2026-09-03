package com.example.sunmireceipt;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AnimationUtils;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.example.sunmireceipt.databinding.ActivityMainBinding;
import com.example.sunmireceipt.databinding.ItemCartNewBinding;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    private ActivityMainBinding binding;
    private PrinterHelper printerHelper;
    private SharedPreferences prefs;
    
    private static final String PREFS_NAME = "SNM_POS_CORE";
    private static final String KEY_STORE_NAME = "st_name";
    private static final String KEY_STORE_ADDR = "st_addr";
    private static final String KEY_FOOTER = "st_foot";
    private static final String KEY_RECEIPT_COUNT = "rc_count";
    private static final String KEY_HISTORY = "rc_history";

    private final List<ProductItem> cart = new ArrayList<>();
    private CartAdapter adapter;
    private double total = 0.0;
    private double discount = 0.0;
    private String orderNote = "";

    private android.graphics.Bitmap selectedBitmap = null;
    private androidx.activity.result.ActivityResultLauncher<String> getContentLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        printerHelper = new PrinterHelper(this);

        setupNavigation();
        setupCheckout();
        setupSettings();
        setupCustomPrint();
        setupStatusUpdate();
        
        showScreen(binding.screenCheckout, "CHECKOUT");
        updateTime();
    }

    private void setupNavigation() {
        binding.bottomNav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_pos) {
                showScreen(binding.screenCheckout, "CHECKOUT");
            } else if (id == R.id.nav_receipt) {
                showScreen(binding.screenReceipts, "RECEIPT");
                updateReceiptPreview();
            } else if (id == R.id.nav_custom) {
                showScreen(binding.screenCustom, "CUSTOM");
            } else if (id == R.id.nav_settings) {
                showScreen(binding.screenSettings, "SETTINGS");
            }
            return true;
        });
    }

    private void showScreen(View screen, String title) {
        binding.screenCheckout.setVisibility(View.GONE);
        binding.screenReceipts.setVisibility(View.GONE);
        binding.screenCustom.setVisibility(View.GONE);
        binding.screenSettings.setVisibility(View.GONE);
        
        screen.setVisibility(View.VISIBLE);
        screen.startAnimation(AnimationUtils.loadAnimation(this, android.R.anim.fade_in));
        binding.tvHeaderTitle.setText(title);
    }

    private void setupCheckout() {
        adapter = new CartAdapter();
        binding.rvCart.setLayoutManager(new LinearLayoutManager(this));
        binding.rvCart.setAdapter(adapter);

        binding.btnAddItem.setOnClickListener(v -> {
            String name = binding.etItemName.getText().toString().trim();
            String priceStr = binding.etItemPrice.getText().toString().trim();
            
            if (name.isEmpty() || priceStr.isEmpty()) {
                Toast.makeText(this, "Enter item details", Toast.LENGTH_SHORT).show();
                return;
            }
            
            try {
                double price = Double.parseDouble(priceStr);
                cart.add(new ProductItem(name, price));
                adapter.notifyItemInserted(cart.size() - 1);
                updateTotal();
                updateReceiptPreview();
                
                binding.etItemName.setText("");
                binding.etItemPrice.setText("");
                binding.etItemName.requestFocus();
            } catch (NumberFormatException e) {
                Toast.makeText(this, "Invalid price", Toast.LENGTH_SHORT).show();
            }
        });

        binding.btnPrint.setOnClickListener(v -> printReceipt());
        binding.btnDiscount.setOnClickListener(v -> showDiscountDialog());
        binding.btnNote.setOnClickListener(v -> showNoteDialog());
        binding.btnClearReceipt.setOnClickListener(v -> resetCheckout());
        
        binding.btnPrint.setOnClickListener(v -> printReceipt());
        binding.btnDiscount.setOnClickListener(v -> showDiscountDialog());
        binding.btnNote.setOnClickListener(v -> showNoteDialog());
        
        binding.etItemPrice.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) {
                // Potential auto-formatter could go here
            }
        });
    }

    private void updateTotal() {
        double subtotal = 0;
        for (ProductItem item : cart) subtotal += item.price;
        
        total = Math.max(0, subtotal - discount);
        
        binding.tvTotal.setText(String.format(Locale.US, "€%.2f", total));
        if (discount > 0) {
            binding.tvDiscountDisplay.setVisibility(View.VISIBLE);
            binding.tvDiscountDisplay.setText(String.format(Locale.US, "-€%.2f", discount));
        } else {
            binding.tvDiscountDisplay.setVisibility(View.GONE);
        }
        
        binding.btnPrint.setEnabled(!cart.isEmpty());
    }

    private void printReceipt() {
        if (cart.isEmpty()) return;

        binding.btnPrint.setText("PRINTING...");
        binding.btnPrint.setEnabled(false);

        int count = prefs.getInt(KEY_RECEIPT_COUNT, 0) + 1;
        String receiptNo = String.format(Locale.US, "%06d", count);
        
        String store = prefs.getString(KEY_STORE_NAME, "SNM POS");
        String addr = prefs.getString(KEY_STORE_ADDR, "");
        String foot = prefs.getString(KEY_FOOTER, "Thank you!");

        double subtotal = 0;
        for (ProductItem item : cart) subtotal += item.price;

        printerHelper.printReceipt(cart, subtotal, discount, total, store, addr, foot, receiptNo, orderNote, new PrinterHelper.PrintCallback() {
            @Override
            public void onPrintSuccess() {
                runOnUiThread(() -> {
                    prefs.edit().putInt(KEY_RECEIPT_COUNT, count).apply();
                    showSuccessOverlay();
                    resetCheckout();
                });
            }

            @Override
            public void onPrintFailed(String error) {
                runOnUiThread(() -> {
                    Toast.makeText(MainActivity.this, error, Toast.LENGTH_SHORT).show();
                    binding.btnPrint.setText("PRINT RECEIPT");
                    binding.btnPrint.setEnabled(true);
                });
            }
        });
    }

    private void showSuccessOverlay() {
        binding.overlaySuccess.setVisibility(View.VISIBLE);
        binding.overlaySuccess.startAnimation(AnimationUtils.loadAnimation(this, android.R.anim.fade_in));
        
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            binding.overlaySuccess.startAnimation(AnimationUtils.loadAnimation(this, android.R.anim.fade_out));
            binding.overlaySuccess.setVisibility(View.GONE);
        }, 2000);
    }

    private void resetCheckout() {
        cart.clear();
        discount = 0;
        orderNote = "";
        adapter.notifyDataSetChanged();
        updateTotal();
        updateReceiptPreview();
        binding.btnPrint.setText("PRINT RECEIPT");
    }

    private void updateReceiptPreview() {
        if (cart.isEmpty()) {
            binding.tvReceiptPreview.setText("\n\n      NO ITEMS YET\n\n");
            return;
        }

        StringBuilder sb = new StringBuilder();
        String store = prefs.getString(KEY_STORE_NAME, "SNM POS");
        
        sb.append("      ").append(store.toUpperCase()).append("\n");
        sb.append("      Terminal by Seb\n");
        sb.append("--------------------------------\n\n");
        
        double subtotal = 0;
        for (ProductItem item : cart) {
            subtotal += item.price;
            sb.append(String.format(Locale.US, "%-20s %9.2f\n", 
                item.name.length() > 20 ? item.name.substring(0, 17) + "..." : item.name, 
                item.price));
        }
        sb.append("--------------------------------\n");
        
        if (discount > 0) {
            sb.append(String.format(Locale.US, "SUBTOTAL %21.2f\n", subtotal));
            sb.append(String.format(Locale.US, "DISCOUNT %21.2f\n", -discount));
            sb.append("--------------------------------\n");
        }
        
        sb.append(String.format(Locale.US, "TOTAL %24.2f\n", total));
        
        if (!orderNote.isEmpty()) {
            sb.append("--------------------------------\n");
            sb.append("NOTE: ").append(orderNote).append("\n");
        }
        
        sb.append("--------------------------------\n");
        sb.append("   Made with love by Seb <3\n");
        
        binding.tvReceiptPreview.setText(sb.toString());
    }



    private void setupCustomPrint() {
        getContentLauncher = registerForActivityResult(new androidx.activity.result.contract.ActivityResultContracts.GetContent(), uri -> {
            if (uri != null) {
                try {
                    selectedBitmap = android.provider.MediaStore.Images.Media.getBitmap(getContentResolver(), uri);
                    binding.ivSelectedImage.setImageBitmap(selectedBitmap);
                    binding.ivSelectedImage.setVisibility(View.VISIBLE);
                } catch (java.io.IOException e) {
                    Toast.makeText(this, "Failed to load image", Toast.LENGTH_SHORT).show();
                }
            }
        });

        binding.btnSelectImage.setOnClickListener(v -> getContentLauncher.launch("image/*"));

        binding.btnPrintCustom.setOnClickListener(v -> {
            String text = binding.etCustomText.getText().toString().trim();
            boolean isBold = binding.switchBold.isChecked();
            
            float fontSize = 24f;
            int checkedFont = binding.toggleFontSize.getCheckedButtonId();
            if (checkedFont == R.id.btnFontSmall) fontSize = 18f;
            else if (checkedFont == R.id.btnFontLarge) fontSize = 32f;

            int alignment = 0; // Left
            int checkedAlign = binding.toggleAlignment.getCheckedButtonId();
            if (checkedAlign == R.id.btnAlignCenter) alignment = 1;
            else if (checkedAlign == R.id.btnAlignRight) alignment = 2;

            PrinterHelper.PrintCallback callback = new PrinterHelper.PrintCallback() {
                @Override public void onPrintSuccess() { runOnUiThread(() -> showSuccessOverlay()); }
                @Override public void onPrintFailed(String error) { runOnUiThread(() -> Toast.makeText(MainActivity.this, error, Toast.LENGTH_SHORT).show()); }
            };

            if (!text.isEmpty() && selectedBitmap != null) {
                printerHelper.printBoth(text, selectedBitmap, alignment, fontSize, isBold, callback);
            } else if (!text.isEmpty()) {
                printerHelper.printCustomText(text, alignment, isBold, fontSize, callback);
            } else if (selectedBitmap != null) {
                printerHelper.printImage(selectedBitmap, alignment, callback);
            } else {
                Toast.makeText(this, "Nothing to print", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void setupSettings() {
        binding.etStoreName.setText(prefs.getString(KEY_STORE_NAME, "SNM POS"));
        binding.etStoreAddress.setText(prefs.getString(KEY_STORE_ADDR, ""));
        binding.etFooter.setText(prefs.getString(KEY_FOOTER, "Thank you!"));

        binding.btnSaveStore.setOnClickListener(v -> {
            prefs.edit()
                .putString(KEY_STORE_NAME, binding.etStoreName.getText().toString())
                .putString(KEY_STORE_ADDR, binding.etStoreAddress.getText().toString())
                .putString(KEY_FOOTER, binding.etFooter.getText().toString())
                .apply();
            Toast.makeText(this, "Store saved", Toast.LENGTH_SHORT).show();
        });

        binding.btnTestPrint.setOnClickListener(v -> printerHelper.printTest(prefs.getString(KEY_STORE_NAME, "SNM POS"), new PrinterHelper.PrintCallback() {
            @Override public void onPrintSuccess() { Toast.makeText(MainActivity.this, "Test OK", Toast.LENGTH_SHORT).show(); }
            @Override public void onPrintFailed(String error) { Toast.makeText(MainActivity.this, error, Toast.LENGTH_SHORT).show(); }
        }));
        
        binding.btnCheckPrinter.setOnClickListener(v -> printerHelper.updateStatus());
    }

    private void setupStatusUpdate() {
        printerHelper.setListener((status, colorResId) -> {
            binding.tvPrinterStatus.setText(status);
            binding.tvPrinterStatus.setTextColor(ContextCompat.getColor(this, colorResId));
            binding.printerStatusIndicator.setBackgroundTintList(ContextCompat.getColorStateList(this, colorResId));
            binding.tvPrinterInfo.setText("Status: " + status);
        });
        printerHelper.connect();
    }

    private void updateTime() {
        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm", Locale.getDefault());
        binding.tvTime.setText(sdf.format(new Date()));
        new Handler(Looper.getMainLooper()).postDelayed(this::updateTime, 60000);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        printerHelper.disconnect();
    }

    private void showDiscountDialog() {
        String[] options = {"5%", "10%", "20%", "Custom", "Clear"};
        new androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Apply Discount")
            .setItems(options, (dialog, which) -> {
                double subtotal = 0;
                for (ProductItem item : cart) subtotal += item.price;
                
                switch (which) {
                    case 0: discount = subtotal * 0.05; break;
                    case 1: discount = subtotal * 0.10; break;
                    case 2: discount = subtotal * 0.20; break;
                    case 3: showCustomDiscountDialog(); return;
                    case 4: discount = 0; break;
                }
                updateTotal();
            }).show();
    }

    private void showCustomDiscountDialog() {
        android.widget.EditText et = new android.widget.EditText(this);
        et.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
        et.setHint("Enter amount (€)");
        new androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Custom Discount")
            .setView(et)
            .setPositiveButton("Apply", (dialog, which) -> {
                try {
                    discount = Double.parseDouble(et.getText().toString());
                    updateTotal();
                } catch (Exception ignored) {}
            })
            .setNegativeButton("Cancel", null)
            .show();
    }

    private void showNoteDialog() {
        android.widget.EditText et = new android.widget.EditText(this);
        et.setText(orderNote);
        et.setHint("e.g. Extra sauce");
        new androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Order Note")
            .setView(et)
            .setPositiveButton("Save", (dialog, which) -> {
                orderNote = et.getText().toString();
                Toast.makeText(this, "Note saved", Toast.LENGTH_SHORT).show();
            })
            .setNegativeButton("Cancel", null)
            .show();
    }





    private class CartAdapter extends RecyclerView.Adapter<CartAdapter.ViewHolder> {
        @NonNull @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new ViewHolder(ItemCartNewBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
        }
        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            ProductItem item = cart.get(position);
            holder.binding.tvItemName.setText(item.name);
            holder.binding.tvItemPrice.setText(String.format(Locale.US, "€%.2f", item.price));
            holder.binding.tvItemQuantity.setText("1 × " + String.format(Locale.US, "€%.2f", item.price));
            holder.binding.btnRemove.setOnClickListener(v -> {
                int pos = holder.getAdapterPosition();
                if (pos != RecyclerView.NO_POSITION) {
                    cart.remove(pos);
                    notifyItemRemoved(pos);
                    updateTotal();
                }
            });
        }
        @Override public int getItemCount() { return cart.size(); }
        class ViewHolder extends RecyclerView.ViewHolder {
            ItemCartNewBinding binding;
            ViewHolder(ItemCartNewBinding binding) { super(binding.getRoot()); this.binding = binding; }
        }
    }
}
