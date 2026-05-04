package com.miladghouila.proscanai;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import java.util.List;

public class HistoryActivity extends AppCompatActivity {

    private RecyclerView recyclerView;
    private View emptyView;
    private HistoryAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_history);

        recyclerView = findViewById(R.id.recyclerHistory);
        emptyView = findViewById(R.id.layoutEmpty);
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        findViewById(R.id.btnClear).setOnClickListener(v -> {
            new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Clear History")
                .setMessage("Are you sure you want to delete all translations?")
                .setPositiveButton("Yes", (dialog, which) -> {
                    HistoryManager.clearHistory(this);
                    loadHistory();
                    Toast.makeText(this, "History cleared", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("No", null)
                .show();
        });

        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        loadHistory();
    }

    private void loadHistory() {
        List<String> history = HistoryManager.getHistory(this);
        if (history.isEmpty()) {
            emptyView.setVisibility(View.VISIBLE);
            recyclerView.setVisibility(View.GONE);
        } else {
            emptyView.setVisibility(View.GONE);
            recyclerView.setVisibility(View.VISIBLE);
            adapter = new HistoryAdapter(history);
            recyclerView.setAdapter(adapter);
        }
    }

    private class HistoryAdapter extends RecyclerView.Adapter<HistoryAdapter.ViewHolder> {
        private List<String> history;

        HistoryAdapter(List<String> history) {
            this.history = history;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_history, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            String text = history.get(position);
            holder.txtText.setText(text);
            holder.btnCopy.setOnClickListener(v -> {
                ClipboardManager cb = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                cb.setPrimaryClip(ClipData.newPlainText("ProScan", text));
                Toast.makeText(HistoryActivity.this, "Copied!", Toast.LENGTH_SHORT).show();
            });
            holder.btnShare.setOnClickListener(v -> {
                android.content.Intent sendIntent = new android.content.Intent();
                sendIntent.setAction(android.content.Intent.ACTION_SEND);
                sendIntent.putExtra(android.content.Intent.EXTRA_TEXT, text);
                sendIntent.setType("text/plain");
                android.content.Intent shareIntent = android.content.Intent.createChooser(sendIntent, null);
                startActivity(shareIntent);
            });
        }

        @Override
        public int getItemCount() {
            return history.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            TextView txtText;
            View btnCopy, btnShare;

            ViewHolder(View itemView) {
                super(itemView);
                txtText = itemView.findViewById(R.id.txtHistoryText);
                btnCopy = itemView.findViewById(R.id.btnCopy);
                btnShare = itemView.findViewById(R.id.btnShare);
            }
        }
    }
}