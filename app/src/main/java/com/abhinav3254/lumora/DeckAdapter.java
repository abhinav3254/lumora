package com.abhinav3254.lumora;

import android.content.Context;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class DeckAdapter extends RecyclerView.Adapter<DeckAdapter.VH> {

    private final Context ctx;
    private final List<String> uris;

    public DeckAdapter(Context ctx, List<String> uris) {
        this.ctx = ctx;
        this.uris = uris;
    }

    @NonNull @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(ctx).inflate(R.layout.item_deck_photo, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int pos) {
        h.img.setImageURI(Uri.parse(uris.get(pos)));
    }

    @Override public int getItemCount() { return uris.size(); }

    static class VH extends RecyclerView.ViewHolder {
        ImageView img;
        VH(@NonNull View v) {
            super(v);
            img = v.findViewById(R.id.iv_deck_photo);
        }
    }
}
