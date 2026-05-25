package com.miladghouila.proscanai;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;
import com.google.android.material.tabs.TabLayout;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import java.util.List;

public class HistoryActivity extends AppCompatActivity {

    private RecyclerView recyclerView;
    private View emptyView;
    private HistoryAdapter adapter;
    private boolean showingFavorites = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_history);

        recyclerView = findViewById(R.id.recyclerHistory);
        emptyView = findViewById(R.id.layoutEmpty);
        TabLayout tabLayout = findViewById(R.id.tabLayout);

        tabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                showingFavorites = tab.getPosition() == 1;
                loadHistory();
            }
            @Override
            public void onTabUnselected(TabLayout.Tab tab) {}
            @Override
            public void onTabReselected(TabLayout.Tab tab) {}
        });

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        
        findViewById(R.id.btnExport).setOnClickListener(v -> {
            List<String> items = showingFavorites ?
                HistoryManager.getFavorites(this) :
                HistoryManager.getHistory(this);
                
            if (items.isEmpty()) {
                Toast.makeText(this, "Nothing to export", Toast.LENGTH_SHORT).show();
                return;
            }
            
            StringBuilder sb = new StringBuilder("LinguScan Archive Export\n\n");
            for (String s : items) sb.append("- ").append(s).append("\n\n");
            
            android.content.Intent intent = new android.content.Intent(android.content.Intent.ACTION_SEND);
            intent.setType("text/plain");
            intent.putExtra(android.content.Intent.EXTRA_TEXT, sb.toString());
            startActivity(android.content.Intent.createChooser(intent, "Export Archive"));
        });

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
        List<String> items = showingFavorites ?
                HistoryManager.getFavorites(this) :
                HistoryManager.getHistory(this);
                
        if (items.isEmpty()) {
            emptyView.setVisibility(View.VISIBLE);
            recyclerView.setVisibility(View.GONE);
            TextView txtEmpty = emptyView.findViewById(R.id.txtEmpty);
            if (txtEmpty != null) {
                txtEmpty.setText(showingFavorites ? "No favorites yet" : "No history yet");
            }
        } else {
            emptyView.setVisibility(View.GONE);
            recyclerView.setVisibility(View.VISIBLE);
            adapter = new HistoryAdapter(items);
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
            
            boolean isFav = HistoryManager.isFavorite(HistoryActivity.this, text);
            holder.btnCopy.setImageResource(isFav ?
                android.R.drawable.btn_star_big_on : 
                android.R.drawable.ic_menu_edit);

            holder.btnCopy.setOnClickListener(v -> {
                HistoryManager.toggleFavorite(HistoryActivity.this, text);
                notifyItemChanged(position);
                Toast.makeText(HistoryActivity.this, 
                    HistoryManager.isFavorite(HistoryActivity.this, text) ? "Saved to Favorites" : "Removed from Favorites", 
                    Toast.LENGTH_SHORT).show();
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
            ImageButton btnCopy, btnShare;

            ViewHolder(View itemView) {
                super(itemView);
                txtText = itemView.findViewById(R.id.txtHistoryText);
                btnCopy = (ImageButton) itemView.findViewById(R.id.btnCopy);
                btnShare = (ImageButton) itemView.findViewById(R.id.btnShare);
            }
        }
    }
}