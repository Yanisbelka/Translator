package com.miladghouila.proscanai;

import android.content.Context;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.util.DisplayMetrics;
import android.view.ContextThemeWrapper;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;

public class FloatingUIManager {

    public interface UIActionListener {
        void onTranslateClicked();
        void onScanModeChanged(boolean isFullScreen);
        void onOcrModeChanged();
        void onLanguageSelected(String code);
        void onBackClicked();
        void onHistoryClicked();
        void onCopyClicked(String text);
        void onSpeakClicked(String text);
    }

    private final Context context;
    private final WindowManager windowManager;
    private final UIActionListener listener;

    private View mainLayout, selectionBox, bubbleView;
    private WindowManager.LayoutParams mainParams, boxParams, bubbleParams;

    private TextView textResult, txtCurrentLang;
    private Spinner languageSpinner;
    private View cardResult;

    private int screenWidth, screenHeight;
    private boolean isInterfaceVisible = false;
    private boolean isFullScreenMode = true;

    public FloatingUIManager(Context context, UIActionListener listener) {
        this.context = context;
        this.listener = listener;
        this.windowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);

        DisplayMetrics dm = new DisplayMetrics();
        windowManager.getDefaultDisplay().getRealMetrics(dm);
        screenWidth = dm.widthPixels;
        screenHeight = dm.heightPixels;

        setupUI();
    }

    private void setupUI() {
        ContextThemeWrapper contextThemeWrapper = new ContextThemeWrapper(context, R.style.Theme_ProScanAI);
        LayoutInflater inflater = LayoutInflater.from(contextThemeWrapper);

        // 1. Setup Selection Box Window
        selectionBox = inflater.inflate(R.layout.layout_selection_box, null);
        boxParams = new WindowManager.LayoutParams(
                600, 400,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);
        boxParams.gravity = Gravity.TOP | Gravity.START;
        boxParams.x = (screenWidth - 600) / 2;
        boxParams.y = screenHeight / 4;
        selectionBox.setVisibility(View.GONE);
        windowManager.addView(selectionBox, boxParams);
        setupBoxTouchListeners();

        // 2. Setup Controls Window (Bottom)
        mainLayout = inflater.inflate(R.layout.layout_floating_screen, null);
        mainParams = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
        );
        mainParams.gravity = Gravity.BOTTOM;
        mainLayout.setVisibility(View.GONE);
        windowManager.addView(mainLayout, mainParams);

        // 3. Setup Floating Bubble Window
        bubbleView = inflater.inflate(R.layout.layout_floating_widget, null);
        bubbleParams = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);
        bubbleParams.gravity = Gravity.TOP | Gravity.START;
        bubbleParams.x = 0;
        bubbleParams.y = screenHeight / 2;
        windowManager.addView(bubbleView, bubbleParams);
        setupBubbleTouchListener();

        // UI Mapping
        textResult = mainLayout.findViewById(R.id.textResult);
        cardResult = mainLayout.findViewById(R.id.cardResult);
        txtCurrentLang = mainLayout.findViewById(R.id.txtCurrentLang);
        languageSpinner = mainLayout.findViewById(R.id.languageSpinner);
        View btnTranslate = mainLayout.findViewById(R.id.btnTranslate);
        View btnLanguage = mainLayout.findViewById(R.id.btnLanguage);
        View btnHistory = mainLayout.findViewById(R.id.btnHistory);
        ImageButton btnBack = mainLayout.findViewById(R.id.btnBack);
        View btnOcrScript = mainLayout.findViewById(R.id.btnOcrScript);
        View btnScanMode = mainLayout.findViewById(R.id.btnScanMode);
        ImageView imgScanMode = mainLayout.findViewById(R.id.imgScanMode);
        View btnCopyResult = mainLayout.findViewById(R.id.btnCopyResult);
        View btnSpeakResult = mainLayout.findViewById(R.id.btnSpeakResult);

        if (imgScanMode != null) {
            imgScanMode.setImageResource(isFullScreenMode ? android.R.drawable.ic_menu_crop : android.R.drawable.ic_menu_view);
        }

        btnCopyResult.setOnClickListener(v -> {
            String text = textResult.getText().toString();
            if (!text.isEmpty() && !text.equals("Reading screen...") && !text.equals("Translating...")) {
                listener.onCopyClicked(text);
            }
        });

        btnSpeakResult.setOnClickListener(v -> {
            String text = textResult.getText().toString();
            if (!text.isEmpty() && !text.equals("Reading screen...") && !text.equals("Translating...")) {
                listener.onSpeakClicked(text);
            }
        });

        btnScanMode.setOnClickListener(v -> {
            isFullScreenMode = !isFullScreenMode;
            if (imgScanMode != null) {
                imgScanMode.setImageResource(isFullScreenMode ? android.R.drawable.ic_menu_crop : android.R.drawable.ic_menu_view);
            }
            if (isFullScreenMode) {
                selectionBox.setVisibility(View.GONE);
                Toast.makeText(context, "Scan Mode: Full Screen", Toast.LENGTH_SHORT).show();
            } else {
                selectionBox.setVisibility(View.VISIBLE);
                Toast.makeText(context, "Scan Mode: Partial Area", Toast.LENGTH_SHORT).show();
            }
            listener.onScanModeChanged(isFullScreenMode);
        });

        btnOcrScript.setOnClickListener(v -> listener.onOcrModeChanged());

        btnBack.setOnClickListener(v -> listener.onBackClicked());

        List<LanguageUtils.LanguageItem> allLanguages = LanguageUtils.getSupportedLanguages(false);
        ArrayAdapter<LanguageUtils.LanguageItem> adapter = new ArrayAdapter<>(contextThemeWrapper, R.layout.spinner_item, allLanguages);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        languageSpinner.setAdapter(adapter);

        // Default to French
        for (int i = 0; i < allLanguages.size(); i++) {
            if (allLanguages.get(i).code.equals("fr")) {
                languageSpinner.setSelection(i);
                if (txtCurrentLang != null) txtCurrentLang.setText("FR");
                break;
            }
        }

        languageSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                String targetLanguage = allLanguages.get(position).code;

                if (txtCurrentLang != null) {
                    txtCurrentLang.setText(targetLanguage.toUpperCase());
                }

                listener.onLanguageSelected(targetLanguage);

                // Restore overlay non-focusable mode AFTER selection
                mainParams.flags |= WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
                mainParams.flags |= WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM;

                windowManager.updateViewLayout(mainLayout, mainParams);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                mainParams.flags |= WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
                mainParams.flags |= WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM;

                windowManager.updateViewLayout(mainLayout, mainParams);
            }
        });

        btnLanguage.setOnClickListener(v -> {
            // Enable focus so Spinner can open
            mainParams.flags &= ~WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
            mainParams.flags &= ~WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM;

            windowManager.updateViewLayout(mainLayout, mainParams);

            languageSpinner.post(() -> languageSpinner.performClick());
        });

        btnHistory.setOnClickListener(v -> listener.onHistoryClicked());

        btnTranslate.setOnClickListener(v -> listener.onTranslateClicked());

        setupResizableResultCard();
    }

    private void setupBubbleTouchListener() {
        View mainBubble = bubbleView.findViewById(R.id.main_bubble);
        mainBubble.setOnTouchListener(new View.OnTouchListener() {
            private int initialX, initialY;
            private float initialTouchX, initialTouchY;
            private long touchStartTime;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        initialX = bubbleParams.x;
                        initialY = bubbleParams.y;
                        initialTouchX = event.getRawX();
                        initialTouchY = event.getRawY();
                        touchStartTime = System.currentTimeMillis();
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        bubbleParams.x = initialX + (int) (event.getRawX() - initialTouchX);
                        bubbleParams.y = initialY + (int) (event.getRawY() - initialTouchY);
                        windowManager.updateViewLayout(bubbleView, bubbleParams);
                        return true;
                    case MotionEvent.ACTION_UP:
                        long duration = System.currentTimeMillis() - touchStartTime;
                        float deltaX = Math.abs(event.getRawX() - initialTouchX);
                        float deltaY = Math.abs(event.getRawY() - initialTouchY);

                        if (duration < 200 && deltaX < 10 && deltaY < 10) {
                            toggleInterface();
                            v.performClick();
                        }
                        return true;
                }
                return false;
            }
        });
    }

    public void toggleInterface() {
        isInterfaceVisible = !isInterfaceVisible;
        if (isInterfaceVisible) {
            mainLayout.setVisibility(View.VISIBLE);
            if (!isFullScreenMode) {
                selectionBox.setVisibility(View.VISIBLE);
            }
            ((ImageView)bubbleView.findViewById(R.id.main_bubble)).setImageResource(android.R.drawable.ic_menu_close_clear_cancel);
        } else {
            mainLayout.setVisibility(View.GONE);
            selectionBox.setVisibility(View.GONE);
            ((ImageView)bubbleView.findViewById(R.id.main_bubble)).setImageResource(android.R.drawable.ic_menu_camera);
        }
    }

    private void setupBoxTouchListeners() {
        View resizeHandle = selectionBox.findViewById(R.id.resizeHandle);
        View boxRoot = selectionBox.findViewById(R.id.boxRoot);

        boxRoot.setOnTouchListener(new View.OnTouchListener() {
            private int initialX, initialY;
            private float initialTouchX, initialTouchY;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        initialX = boxParams.x;
                        initialY = boxParams.y;
                        initialTouchX = event.getRawX();
                        initialTouchY = event.getRawY();
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        boxParams.x = initialX + (int) (event.getRawX() - initialTouchX);
                        boxParams.y = initialY + (int) (event.getRawY() - initialTouchY);
                        windowManager.updateViewLayout(selectionBox, boxParams);
                        return true;
                    case MotionEvent.ACTION_UP:
                        v.performClick();
                        return true;
                }
                return false;
            }
        });

        resizeHandle.setOnTouchListener(new View.OnTouchListener() {
            private int initialWidth, initialHeight;
            private float initialTouchX, initialTouchY;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        initialWidth = boxParams.width;
                        initialHeight = boxParams.height;
                        initialTouchX = event.getRawX();
                        initialTouchY = event.getRawY();
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        int newWidth = initialWidth + (int) (event.getRawX() - initialTouchX);
                        int newHeight = initialHeight + (int) (event.getRawY() - initialTouchY);
                        boxParams.width = Math.max(200, newWidth);
                        boxParams.height = Math.max(150, newHeight);
                        windowManager.updateViewLayout(selectionBox, boxParams);
                        return true;
                    case MotionEvent.ACTION_UP:
                        v.performClick();
                        return true;
                }
                return true;
            }
        });
    }

    private void setupResizableResultCard() {
        cardResult.setOnTouchListener(new View.OnTouchListener() {
            private int initialHeight;
            private float initialTouchY;
            private boolean isResizing = false;
            private static final int TOUCH_THRESHOLD = 80;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        float y = event.getY();
                        if (y < TOUCH_THRESHOLD) {
                            isResizing = true;
                            initialHeight = v.getHeight();
                            initialTouchY = event.getRawY();
                            return true;
                        }
                        break;
                    case MotionEvent.ACTION_MOVE:
                        if (isResizing) {
                            float deltaY = initialTouchY - event.getRawY();
                            int newHeight = (int) (initialHeight + deltaY);
                            int minHeight = (int) (150 * context.getResources().getDisplayMetrics().density);
                            int maxHeight = screenHeight / 2;
                            ViewGroup.LayoutParams lp = v.getLayoutParams();
                            lp.height = Math.max(minHeight, Math.min(newHeight, maxHeight));
                            v.setLayoutParams(lp);
                            return true;
                        }
                        break;
                    case MotionEvent.ACTION_UP:
                        isResizing = false;
                        v.performClick();
                        return true;
                }
                return false;
            }
        });
    }

    public void setStatusText(String text) {
        textResult.setText(text);
        cardResult.setVisibility(View.VISIBLE);
    }

    public void hideUIForCapture() {
        mainLayout.setVisibility(View.GONE);
        selectionBox.setVisibility(View.GONE);
        bubbleView.setVisibility(View.GONE);
    }

    public void restoreUI() {
        if (isInterfaceVisible) {
            mainLayout.setVisibility(View.VISIBLE);
            if (!isFullScreenMode) {
                selectionBox.setVisibility(View.VISIBLE);
            }
        }
        bubbleView.setVisibility(View.VISIBLE);
    }

    public Rect getSelectionRect() {
        return new Rect(boxParams.x, boxParams.y, boxParams.x + boxParams.width, boxParams.y + boxParams.height);
    }

    public void onDestroy() {
        if (mainLayout != null) windowManager.removeView(mainLayout);
        if (selectionBox != null) windowManager.removeView(selectionBox);
        if (bubbleView != null) windowManager.removeView(bubbleView);
    }
}
