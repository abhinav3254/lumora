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

public class SelectedPhotosAdapter extends RecyclerView.Adapter<SelectedPhotosAdapter.PhotoVH> {

    private final Context context;
    private final List<Uri> uris;

    public SelectedPhotosAdapter(Context context, List<Uri> uris) {
        this.context = context;
        this.uris = uris;
    }

    @NonNull
    @Override
    public PhotoVH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_photo, parent, false);
        return new PhotoVH(view);
    }

    @Override
    public void onBindViewHolder(@NonNull PhotoVH holder, int position) {
        holder.imageView.setImageURI(uris.get(position));
    }

    @Override
    public int getItemCount() {
        return uris.size();
    }

    static class PhotoVH extends RecyclerView.ViewHolder {
        ImageView imageView;

        PhotoVH(@NonNull View itemView) {
            super(itemView);
            imageView = itemView.findViewById(R.id.iv_photo);
        }
    }
}