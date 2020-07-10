package com.example.scanin;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.recyclerview.widget.RecyclerView;

import java.io.IOException;
import java.util.ArrayList;

public class RecyclerViewGridAdapter extends RecyclerView.Adapter<RecyclerViewGridAdapter.GridViewHolder> {

    private ArrayList<ImageData> mDataset = new ArrayList<ImageData>();
    public static class GridViewHolder extends RecyclerView.ViewHolder{
        ImageView imageView;
        public GridViewHolder(View view){
            super(view);
            imageView =view.findViewById(R.id.image_thumbnail);
        }
    }

    public RecyclerViewGridAdapter(ArrayList<ImageData> mDataset){
        this.mDataset = mDataset;
    }

    public RecyclerViewGridAdapter.GridViewHolder onCreateViewHolder(ViewGroup parent, int viewtype){
        // create a new view
        int layoutIdForImageAdapter =R.layout.image_grid_item;
        LayoutInflater inflater =LayoutInflater.from(parent.getContext());
        View view =inflater.inflate(layoutIdForImageAdapter, parent, false);
//        int height = parent.getMeasuredHeight() / 4;
//        view.setMinimumHeight(height);
        GridViewHolder gridViewHolder = new GridViewHolder(view);
        return gridViewHolder;
    }

    // Replace the contents of a view (invoked by the layout manager)
    @Override
    public void onBindViewHolder(GridViewHolder holder, int position) {
        // - get element from your dataset at this position
        // - replace the contents of the view with that element
        ImageData imageData = mDataset.get(position);
        if(imageData.getOriginalBitmap() == null){
            try {
                imageData.setOriginalBitmap(holder.imageView.getContext());
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

//        holder.textView.setText(mDataset.get(position).getFileName().toString());
        holder.imageView.setImageBitmap(imageData.getThumbnail());
    }

    @Override
    public int getItemCount() {
        return mDataset.size();
    }

    public void setmDataset(ArrayList<ImageData> mDataset){
        this.mDataset = mDataset;
    }
}
