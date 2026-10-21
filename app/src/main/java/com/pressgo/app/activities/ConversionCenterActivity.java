package com.pressgo.app.activities;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

import androidx.appcompat.app.AppCompatActivity;

import com.pressgo.app.databinding.ActivityConversionCenterBinding;

public class ConversionCenterActivity extends AppCompatActivity {
    private static final String TAG = "ConversionCenter";

    private ActivityConversionCenterBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "onCreate");
        binding = ActivityConversionCenterBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        setSupportActionBar(binding.toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("Document Conversion Center");
        }
        binding.toolbar.setNavigationOnClickListener(v -> finish());

        setupConversionCards();
    }

    private void setupConversionCards() {
        binding.cardWordToPdf.setOnClickListener(v -> launchOptions("WORD_TO_PDF", "Word to PDF Converter"));
        binding.cardPptxToPdf.setOnClickListener(v -> launchOptions("PPTX_TO_PDF", "PowerPoint to PDF Converter"));
        binding.cardPdfToWord.setOnClickListener(v -> launchOptions("PDF_TO_WORD", "PDF to Word Converter"));
        binding.cardPdfToPptx.setOnClickListener(v -> launchOptions("PDF_TO_PPTX", "PDF to PowerPoint Converter"));
        binding.cardImagesToPdf.setOnClickListener(v -> launchOptions("IMAGES_TO_PDF", "Images to PDF"));
        binding.cardPdfToImages.setOnClickListener(v -> launchOptions("PDF_TO_IMAGES", "PDF to Images"));
        binding.cardMergePdf.setOnClickListener(v -> launchOptions("MERGE_PDF", "Merge Multiple PDFs"));
        binding.cardSplitPdf.setOnClickListener(v -> launchOptions("SPLIT_PDF", "Split PDF Pages"));
        binding.cardPdfToTxt.setOnClickListener(v -> launchOptions("PDF_TO_TXT", "PDF to Text Extractor"));
        binding.cardCodeToPdf.setOnClickListener(v -> launchOptions("CODE_TO_PDF", "Code / Script to PDF"));
        binding.cardTxtToPdf.setOnClickListener(v -> launchOptions("TXT_TO_PDF", "Text to PDF"));
        binding.cardImageConvert.setOnClickListener(v -> launchOptions("IMAGE_FORMAT", "Image Format (JPG/PNG/WEBP)"));

        binding.btnViewHistory.setOnClickListener(v -> {
            Intent intent = new Intent(this, ConversionHistoryActivity.class);
            intent.putExtra(ConversionHistoryActivity.EXTRA_INITIAL_TAB, ConversionHistoryActivity.TAB_CONVERTED);
            startActivity(intent);
        });
    }

    private void launchOptions(String type, String title) {
        Intent intent = new Intent(this, ConversionOptionsActivity.class);
        intent.putExtra("type", type);
        intent.putExtra("title", title);
        startActivity(intent);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "onDestroy");
    }
}
