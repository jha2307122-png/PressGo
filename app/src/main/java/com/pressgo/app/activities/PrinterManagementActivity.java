package com.pressgo.app.activities;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.pressgo.app.R;
import com.pressgo.app.adapters.PrinterAdapter;
import com.pressgo.app.databinding.ActivityPrinterManagementBinding;
import com.pressgo.app.models.Printer;
import com.pressgo.app.repositories.PrinterRepository;
import com.pressgo.app.utils.SessionManager;

import java.util.ArrayList;
import java.util.List;

public class PrinterManagementActivity extends AppCompatActivity {
    private static final String TAG = "PrinterManagement";

    private ActivityPrinterManagementBinding binding;
    private final List<Printer> printers = new ArrayList<>();
    private PrinterAdapter adapter;
    private String shopId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "onCreate");
        binding = ActivityPrinterManagementBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        shopId = new SessionManager(this).getShopId();
        if (shopId == null || shopId.isEmpty()) {
            shopId = "shop_campus_main"; // Default demo shop
        }

        setSupportActionBar(binding.toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("Printer Fleet Management");
        }
        binding.toolbar.setNavigationOnClickListener(v -> finish());

        setupRecyclerView();
        setupFab();
        loadPrinters();
    }

    private void setupRecyclerView() {
        adapter = new PrinterAdapter(printers, this::showPrinterOptionsDialog);
        binding.rvPrinters.setLayoutManager(new LinearLayoutManager(this));
        binding.rvPrinters.setAdapter(adapter);

        binding.swipeRefreshLayout.setOnRefreshListener(this::loadPrinters);
    }

    private void setupFab() {
        binding.fabAddPrinter.setOnClickListener(v -> showAddPrinterDialog());
    }

    private void loadPrinters() {
        binding.swipeRefreshLayout.setRefreshing(true);
        PrinterRepository.getInstance().getShopPrinters(shopId, list -> {
            binding.swipeRefreshLayout.setRefreshing(false);
            printers.clear();
            if (list != null && !list.isEmpty()) {
                printers.addAll(list);
            } else {
                // Initialize default demo printers
                initDefaultPrinters();
            }
            adapter.notifyDataSetChanged();
            updateEmptyState();
        }, e -> {
            binding.swipeRefreshLayout.setRefreshing(false);
            Log.e(TAG, "Error fetching printers", e);
            initDefaultPrinters();
        });
    }

    private void initDefaultPrinters() {
        printers.clear();
        Printer p1 = new Printer();
        p1.setId("printer_hp_laserjet_01");
        p1.setShopId(shopId);
        p1.setName("HP LaserJet Enterprise M608");
        p1.setModel("LaserJet B&W High Speed");
        p1.setStatus(Printer.STATUS_AVAILABLE);
        p1.setPaperLevel(85);
        p1.setInkLevel(70);
        p1.setTonerLevel(90);
        p1.setHasColorSupport(false);
        p1.setHasDuplexSupport(true);

        Printer p2 = new Printer();
        p2.setId("printer_canon_imagepro_02");
        p2.setShopId(shopId);
        p2.setName("Canon imagePROGRAF PRO-1000");
        p2.setModel("Color Photo & Document Station");
        p2.setStatus(Printer.STATUS_BUSY);
        p2.setCurrentJobId("PG-20261021-4821");
        p2.setPaperLevel(45);
        p2.setInkLevel(20);
        p2.setTonerLevel(55);
        p2.setHasColorSupport(true);
        p2.setHasDuplexSupport(true);

        Printer p3 = new Printer();
        p3.setId("printer_xerox_versalink_03");
        p3.setShopId(shopId);
        p3.setName("Xerox VersaLink C405");
        p3.setModel("Multifunction Color Laser");
        p3.setStatus(Printer.STATUS_OUT_OF_SERVICE);
        p3.setPaperLevel(10);
        p3.setInkLevel(5);
        p3.setTonerLevel(15);
        p3.setHasColorSupport(true);
        p3.setHasDuplexSupport(true);

        printers.add(p1);
        printers.add(p2);
        printers.add(p3);

        PrinterRepository.getInstance().addPrinter(p1, t -> {});
        PrinterRepository.getInstance().addPrinter(p2, t -> {});
        PrinterRepository.getInstance().addPrinter(p3, t -> {});

        adapter.notifyDataSetChanged();
        updateEmptyState();
    }

    private void showPrinterOptionsDialog(Printer printer) {
        String[] options = {"Mark as AVAILABLE", "Mark as BUSY", "Mark OUT OF SERVICE", "Update Paper & Toner Levels"};
        new AlertDialog.Builder(this)
                .setTitle(printer.getName())
                .setItems(options, (dialog, which) -> {
                    if (which == 0) {
                        updatePrinterStatus(printer, Printer.STATUS_AVAILABLE);
                    } else if (which == 1) {
                        updatePrinterStatus(printer, Printer.STATUS_BUSY);
                    } else if (which == 2) {
                        updatePrinterStatus(printer, Printer.STATUS_OUT_OF_SERVICE);
                    } else if (which == 3) {
                        showUpdateLevelsDialog(printer);
                    }
                })
                .show();
    }

    private void updatePrinterStatus(Printer printer, String status) {
        PrinterRepository.getInstance().updatePrinterStatus(printer.getId(), status, task -> {
            printer.setStatus(status);
            adapter.notifyDataSetChanged();
            Toast.makeText(this, "Printer status updated to " + status, Toast.LENGTH_SHORT).show();
        });
    }

    private void showUpdateLevelsDialog(Printer printer) {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_printer_levels, null);
        SeekBar sbPaper = dialogView.findViewById(R.id.sbPaper);
        SeekBar sbInk = dialogView.findViewById(R.id.sbInk);
        SeekBar sbToner = dialogView.findViewById(R.id.sbToner);

        sbPaper.setProgress(printer.getPaperLevel());
        sbInk.setProgress(printer.getInkLevel());
        sbToner.setProgress(printer.getTonerLevel());

        new AlertDialog.Builder(this)
                .setTitle("Update Supply Levels")
                .setView(dialogView)
                .setPositiveButton("Save", (dialog, which) -> {
                    int p = sbPaper.getProgress();
                    int i = sbInk.getProgress();
                    int t = sbToner.getProgress();
                    PrinterRepository.getInstance().updatePrinterLevels(printer.getId(), p, i, t, task -> {
                        printer.setPaperLevel(p);
                        printer.setInkLevel(i);
                        printer.setTonerLevel(t);
                        adapter.notifyDataSetChanged();
                        Toast.makeText(this, "Supplies updated", Toast.LENGTH_SHORT).show();
                    });
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showAddPrinterDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Add New Printer");
        final EditText input = new EditText(this);
        input.setHint("Printer Name / Model");
        builder.setView(input);

        builder.setPositiveButton("Add", (dialog, which) -> {
            String name = input.getText().toString().trim();
            if (!name.isEmpty()) {
                Printer p = new Printer();
                p.setName(name);
                p.setShopId(shopId);
                p.setStatus(Printer.STATUS_AVAILABLE);
                p.setPaperLevel(100);
                p.setInkLevel(100);
                p.setTonerLevel(100);
                PrinterRepository.getInstance().addPrinter(p, task -> loadPrinters());
            }
        });
        builder.setNegativeButton("Cancel", null);
        builder.show();
    }

    private void updateEmptyState() {
        if (printers.isEmpty()) {
            binding.emptyStateLayout.setVisibility(View.VISIBLE);
            binding.rvPrinters.setVisibility(View.GONE);
        } else {
            binding.emptyStateLayout.setVisibility(View.GONE);
            binding.rvPrinters.setVisibility(View.VISIBLE);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "onDestroy");
    }
}
