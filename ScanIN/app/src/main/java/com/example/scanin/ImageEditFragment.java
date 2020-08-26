package com.example.scanin;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.PointF;
import android.graphics.drawable.Drawable;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.PagerSnapHelper;
import androidx.recyclerview.widget.RecyclerView;

import com.example.scanin.DatabaseModule.Document;
import com.example.scanin.DatabaseModule.DocumentAndImageInfo;
import com.example.scanin.DatabaseModule.ImageInfo;
import com.example.scanin.DatabaseModule.Repository;
import com.example.scanin.HomeModule.MainActivity;
import com.example.scanin.ImageDataModule.BrightnessFilterTransformation1;
import com.example.scanin.ImageDataModule.ContrastFilterTransformation1;
import com.example.scanin.ImageDataModule.CropTransformation;
import com.example.scanin.ImageDataModule.FilterTransformation;
import com.example.scanin.ImageDataModule.ImageData;
import com.example.scanin.ImageDataModule.ImageEditUtil;
import com.example.scanin.StateMachineModule.MachineActions;
import com.example.scanin.Utils.FileUtils;
import com.squareup.picasso.Picasso;
import com.squareup.picasso.Target;

import org.jetbrains.annotations.NotNull;
import org.opencv.core.Point;

import java.io.File;
import java.io.FileOutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import io.reactivex.Observable;
import io.reactivex.Single;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.schedulers.Schedulers;
import jp.wasabeef.picasso.transformations.gpu.BrightnessFilterTransformation;
import kotlin.collections.ArraysKt;
import kotlin.jvm.internal.Intrinsics;

import static com.example.scanin.ImageDataModule.ImageData.rotateBitmap;
import static com.example.scanin.ImageDataModule.ImageEditUtil.convertArrayList2Map;
import static com.example.scanin.ImageDataModule.ImageEditUtil.convertMap2ArrayList;
import static com.example.scanin.ImageDataModule.ImageEditUtil.getDefaultPoints;
import static com.example.scanin.ImageDataModule.ImageEditUtil.rotateCropPoints;
import static com.example.scanin.ImageDataModule.ImageEditUtil.scalePoints;

/**
 * A simple {@link Fragment} subclass.
 * Use the {@link ImageEditFragment#newInstance} factory method to
 * create an instance of this fragment.
 */
public class ImageEditFragment extends Fragment {

    private static final String FILENAME_FORMAT = "yyyy-MM-dd-HH-mm-ss-SSS";
    // TODO: Rename parameter arguments, choose names that match
    // the fragment initialization parameters, e.g. ARG_ITEM_NUMBER
    private static final String ARG_PARAM1 = "param1";
    private static final String ARG_PARAM2 = "param2";
    private FrameLayout holderImageCrop;
    // TODO: Rename and change types of parameters
    private String mParam1;
    private String mParam2;
    private String TAG = "EDIT_FRAG";
    private View rootView;
    private View mainView;
    private View cropView;
    private PolygonView polygonView;
    private ProgressBar progressBar;
    private ImageData currentImg;
    private ImageView cropImageView;
    private HorizontalScrollView filterContainer;
//    private LinearSnapHelper pagerSnapHelper;
    private PagerSnapHelper pagerSnapHelper;
    protected CompositeDisposable disposable = new CompositeDisposable();
    private Bitmap selectedImage;
    private DocumentAndImageInfo documentAndImageInfo;
    RecyclerViewEditAdapter mAdapter = null;
    int CurrentMachineState = -1;
    Integer adapterPosition=0;
    RecyclerView recyclerView;
    private int cropHeight;
    private int cropWidth;
    private int filterPreviewHeight = 100;
    private RecyclerView.LayoutManager layoutManager;
    boolean filterVisible = false;
    boolean bacVisible = false;
    private int imageEffectSelected = -1;
    public final int BRIGHTNESS = 0;
    public final int CONTRAST = 1;
    private float effectVal;
    private int newAdapterPosition = 0;

    private void initializeCropping() {
        ViewTreeObserver vto = cropImageView.getViewTreeObserver();
        vto.addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
            public boolean onPreDraw() {
                // Remove after the first run so it doesn't fire forever
                cropImageView.getViewTreeObserver().removeOnPreDrawListener(this);
                int height = cropImageView.getMeasuredHeight();
                int width = cropImageView.getMeasuredWidth();

                Log.d("Dimension", "MainCrop: " + width + " - " + height);

                Map<Integer, PointF> pointFs = convertArrayList2Map(currentImg.getCropPosition());
                if (pointFs == null) {
                    Log.d("ImageEditFragment", "pointFs is null");
                }
                try {
                    // if nothing in database
                    if (pointFs == null) {
                        ArrayList<Point> points = currentImg.getBestPoints();
                        currentImg.setCropPosition(points);
                        pointFs = convertArrayList2Map(points);
                    // if database has cropPoints in orig config. Convert to the current rotation config.
                    } else {
                        int rotValue = 0;
                        int rotationConfig = currentImg.getRotationConfig();
                        while (rotValue != rotationConfig) {
                            pointFs = rotateCropPoints(pointFs, currentImg.getWidth(), currentImg.getHeight(), rotValue);
                            rotValue = (rotValue + 1) % 4;
                        }
                    }
                    double scale = currentImg.getScale(width, height);
                    pointFs = scalePoints(pointFs, (float) scale);

                    polygonView.setVisibility(View.VISIBLE);

                    int padding = (int) getResources().getDimension(R.dimen.scanPadding);

                    FrameLayout.LayoutParams layoutParams = new FrameLayout.LayoutParams(width + 2 * padding, height + 2 * padding);
                    layoutParams.gravity = Gravity.CENTER;

                    polygonView.setLayoutParams(layoutParams);
                    polygonView.setPointColor(getResources().getColor(R.color.colorPrimary));
                    polygonView.setPoints(pointFs);
                    polygonView.invalidate();

                } catch (Exception e) {
                    e.printStackTrace();
                }

                return true;
            }
        });


    }

    private OnClickListener mainCrop = new OnClickListener() {
        @Override
        public void onClick(View view) {
            View currentView = pagerSnapHelper.findSnapView(layoutManager);
            if(currentView == null) return;
            adapterPosition = layoutManager.getPosition(currentView);
            ImageInfo imgInfo = documentAndImageInfo.getImages().get(adapterPosition);

            Log.d (getTag(), imgInfo.getUri().toString());

            mainView.setVisibility(View.GONE);
            cropView.setVisibility(View.VISIBLE);
            currentImg = new ImageData(imgInfo);

            try {
                currentImg.setOriginalBitmap(cropImageView.getContext());
                currentImg.setCurrentBitmap(rotateBitmap(currentImg.getCurrentBitmap(),
                        90.0f * imgInfo.getRotationConfig()));
                selectedImage = currentImg.getSmallCurrentImage(cropImageView.getContext());
                hideProgressBar();
                cropImageView.setImageBitmap(selectedImage);
            } catch (Exception e) {
                Log.e(getTag(), "IO ERROR in loading image in crop");
            }
            initializeCropping();
        }
    };

    private OnClickListener cropApply = new OnClickListener() {
        @Override
        public void onClick(View view) {
            Map <Integer, PointF> points = polygonView.getPoints();
            int width = cropImageView.getMeasuredWidth();
            int height = cropImageView.getMeasuredHeight();
            double scale = currentImg.getScale(width, height);
            points = scalePoints(points, (float) (1.0 / scale));

            // Before storing the crop points must be converted for the original configuration.
            // This is done because rotating in edit mode (not crop edit) mode will now not
            // require rotating crop Points.
            int rotationConfig = currentImg.getRotationConfig();
            int origWidth = currentImg.getWidth();
            int origHeight = currentImg.getHeight();

            while (rotationConfig != 0) {
                points = rotateCropPoints(points, origWidth, origHeight, rotationConfig);
                rotationConfig = (rotationConfig + 1) % 4;
            }

            // The crop position scale is according to the image that was loaded in ImageData.
            documentAndImageInfo.getImages().get(adapterPosition).setCropPositionMap(points);
            documentAndImageInfo.getImages().get(adapterPosition).setRotationConfig(currentImg.getRotationConfig());
            mAdapter.notifyDataSetChanged();

            cropView.setVisibility(View.GONE);
            mainView.setVisibility(View.VISIBLE);
        }
    };

    private OnClickListener cropBack = new OnClickListener() {
        @Override
        public void onClick(View view) {
            cropView.setVisibility(View.GONE);
            mainView.setVisibility(View.VISIBLE);
        }
    };

    private OnClickListener cropAutoDetect = new OnClickListener() {
        @Override
        public void onClick(View view) {
            showProgressBar();
            int height = cropImageView.getMeasuredHeight();
            int width = cropImageView.getMeasuredWidth();

            Log.i("Dimension", "Auto Detect: " + width+" - "+height);

            double scale = currentImg.getScale(width, height);
            ArrayList<Point> points = currentImg.getBestPoints();
            currentImg.setCropPosition(points);
            Map<Integer, PointF> pointFs = convertArrayList2Map(points);
            pointFs = scalePoints(pointFs, (float) scale);
            polygonView.setPoints(pointFs);
            polygonView.invalidate();
            hideProgressBar();
        }
    };

    private OnClickListener cropNoCrop = new OnClickListener() {
        @Override
        public void onClick(View view) {
            showProgressBar();
            int width = cropImageView.getMeasuredWidth();
            int height = cropImageView.getMeasuredHeight();

            Log.i("Dimension", "No Crop: " + width+" - "+height);

            Map <Integer, PointF> default_points = getDefaultPoints (width, height);
            polygonView.setPoints(default_points);
            polygonView.invalidate();
            hideProgressBar();
        }
    };

    private OnClickListener cropRotate = new OnClickListener() {
        @Override
        public void onClick(View view) {
            Map <Integer, PointF> points = polygonView.getPoints();
            int width = cropImageView.getMeasuredWidth();
            int height = cropImageView.getMeasuredHeight();
            double scale = currentImg.getScale(width, height);
            points = scalePoints(points, (float) (1.0 / scale));
            ArrayList <Point> points_ar = convertMap2ArrayList(points);
            currentImg.setCropPosition(points_ar);
            currentImg.rotateBitmap();
            selectedImage = currentImg.getSmallCurrentImage(cropImageView.getContext());
            cropImageView.setImageBitmap(selectedImage);

            ViewTreeObserver vto = cropImageView.getViewTreeObserver();
            vto.addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
                public boolean onPreDraw() {
                    // Remove after the first run so it doesn't fire forever
                    cropImageView.getViewTreeObserver().removeOnPreDrawListener(this);
                    int height = cropImageView.getMeasuredHeight();
                    int width = cropImageView.getMeasuredWidth();

                    Log.d("Dimension", "MainCrop: " + width + " - " + height);

                    double scale = currentImg.getScale(width, height);
                    try {
                        Map <Integer, PointF> pointFs = convertArrayList2Map(currentImg.getCropPosition());
                        pointFs = scalePoints(pointFs, (float) scale);

                        polygonView.setPoints(pointFs);
                        int padding = (int) getResources().getDimension(R.dimen.scanPadding);
                        FrameLayout.LayoutParams layoutParams = new FrameLayout.LayoutParams(width + 2 * padding, height + 2 * padding);
                        layoutParams.gravity = Gravity.CENTER;
                        polygonView.setLayoutParams(layoutParams);
                        polygonView.invalidate();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }

                    return true;
                }
            });
        }
    };

    private void setViewInteract(View view, boolean canDo) {
        view.setEnabled(canDo);
        if (view instanceof ViewGroup) {
            for (int i = 0; i < ((ViewGroup) view).getChildCount(); i++) {
                setViewInteract(((ViewGroup) view).getChildAt(i), canDo);
            }
        }
    }

    protected void showProgressBar() {
        //setViewInteract(rootView, false);
        progressBar.setVisibility(View.VISIBLE);
    };

    protected void hideProgressBar() {
        //setViewInteract(rootView, false);
        progressBar.setVisibility(View.GONE);
    };

    public ImageEditFragment() {
        // Required empty public constructor
    }

    ImageEditFragment.ImageEditFragmentCallback imageEditFragmentCallback;

    public interface ImageEditFragmentCallback{
        void onCreateEditCallback();
        void onClickEditCallback(int action);
        void editDeleteImageCallback(int position);
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        try {
            imageEditFragmentCallback = (ImageEditFragment.ImageEditFragmentCallback) context;
        }
        catch (ClassCastException e){
            throw new ClassCastException(context.toString()
                    + "must implement imageEditFragmentCallback");
        }
    }

    /**
     * Use this factory method to create a new instance of
     * this fragment using the provided parameters.
     *
     * @param param1 Parameter 1.
     * @param param2 Parameter 2.
     * @return A new instance of fragment ImageEditFragment.
     */
    // TODO: Rename and change types and number of parameters
    public static ImageEditFragment newInstance(String param1, String param2) {
        ImageEditFragment fragment = new ImageEditFragment();
        Bundle args = new Bundle();
        args.putString(ARG_PARAM1, param1);
        args.putString(ARG_PARAM2, param2);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            mParam1 = getArguments().getString(ARG_PARAM1);
            mParam2 = getArguments().getString(ARG_PARAM2);
        }
        Log.d(TAG, "onCreateCalled");
    }

    @SuppressLint("CheckResult")
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        Log.d ("imageEditFragment", "On Create View");
        // Inflate the layout for this fragment
        View rootView = inflater.inflate(R.layout.fragment_image_edit, container, false);
        ((ScanActivity)getActivity()).CurrentMachineState = this.CurrentMachineState;

        cropView = rootView.findViewById(R.id.rlContainer);
        mainView = rootView.findViewById(R.id.edit_main);
        cropView.setVisibility(View.GONE);
        polygonView = rootView.findViewById(R.id.polygonView);
        progressBar = rootView.findViewById(R.id.progressBar);
        cropImageView = (ImageView) rootView.findViewById(R.id.cropImageView);
        holderImageCrop = rootView.findViewById(R.id.holderImageCrop);
        recyclerView = (RecyclerView)rootView.findViewById(R.id.recyclerview_image);
        filterContainer = (HorizontalScrollView)rootView.findViewById(R.id.filter_scroll_view);

        layoutManager = new LinearLayoutManager(getContext(), LinearLayoutManager.HORIZONTAL, false);
//        SpeedyLinearLayoutManager layoutManager = new SpeedyLinearLayoutManager(getContext(), LinearLayoutManager.HORIZONTAL, false);
        recyclerView.setLayoutManager(layoutManager);
        mAdapter = new RecyclerViewEditAdapter(null, (ScanActivity) getActivity());
        recyclerView.setAdapter(mAdapter);
        recyclerView.setHasFixedSize(true);
        recyclerView.setItemViewCacheSize(20);

        recyclerView.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                // Remove after the first run so it doesn't fire forever
                recyclerView.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                //recyclerView.scrollToPosition(newAdapterPosition);
                recyclerView.smoothScrollToPosition(newAdapterPosition);
            }
        });

//        recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
//            @Override
//            public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {
//                super.onScrollStateChanged(recyclerView, newState);
//                if(newState == RecyclerView.SCROLL_STATE_IDLE){
//                    Integer position = ((LinearLayoutManager)layoutManager).findFirstVisibleItemPosition();
//                    setCurrentAdapterPosition(position);
//                }
//            }
//        });

//        pagerSnapHelper = new LinearSnapHelper();
        pagerSnapHelper = new PagerSnapHelper();
        pagerSnapHelper.attachToRecyclerView(recyclerView);

        rootView.findViewById(R.id.edit_add_more).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                view.setClickable(false);
                imageEditFragmentCallback.onClickEditCallback(MachineActions.EDIT_ADD_MORE);
            }
        });

        rootView.findViewById(R.id.reorder).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                view.setClickable(false);
                imageEditFragmentCallback.onClickEditCallback(MachineActions.REORDER);
            }
        });

        rootView.findViewById(R.id.save_in_gallery).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if(mAdapter.getItemCount() >= 0){
                    View currentView = pagerSnapHelper.findSnapView(layoutManager);
                    if(currentView == null) return;
                    adapterPosition = layoutManager.getPosition(currentView);
                    ImageInfo imageInfo = documentAndImageInfo.getImages().get(adapterPosition);

                    AlertDialog.Builder alert = new AlertDialog.Builder(getContext(), R.style.AlertDialogCustom);

                    TextView textView = new TextView(getContext());
                    textView.setText("Save in gallery");
                    textView.setPadding(20, 30, 20, 30);
                    textView.setTextSize(20F);
                    textView.setTextColor(Color.BLACK);
                    alert.setCustomTitle(textView);

                    EditText editText = new EditText(getContext());
                    String new_name = new SimpleDateFormat(FILENAME_FORMAT, Locale.US).format(System.currentTimeMillis());
                    editText.setText(new_name);
                    editText.setTextSize(20F);
                    editText.setTextColor(Color.BLACK);
                    alert.setView(editText);

                    alert.setPositiveButton("Save", new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int whichButton) {
                            //imageEditFragmentCallback.editDeleteImageCallback(adapterPosition);
                            String new_name_edited = editText.getText().toString();
                            Log.d ("imageSave", new_name_edited + " trying to save in gallery");
                            if (FileUtils.validateFileName(new_name_edited)) {
                                try {
                                    saveInGalleryHelper (imageInfo, new_name_edited);

                                    Toast.makeText(getContext(),
                                            "File saved successfully in Pictures folder.", Toast.LENGTH_SHORT)
                                            .show();
                                } catch (Exception e) {
                                    Log.e("imageSave", "Exception " + e.getMessage());
                                    Toast.makeText(getContext(),
                                            "File save failed.", Toast.LENGTH_SHORT)
                                            .show();
                                    e.printStackTrace();
                                }

                            } else {
                                Toast.makeText(getContext(),
                                        "Allowed characters A-Z, a-z, 0-9, _, -", Toast.LENGTH_LONG)
                                        .show();
                            }
                        }
                    });

                    alert.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int whichButton) {
                            // what ever you want to do with No option.
                        }
                    });

                    alert.show();
                }
            }
        });

        rootView.findViewById(R.id.apply_edits).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                view.setClickable(false);
                imageEditFragmentCallback.onClickEditCallback(MachineActions.EDIT_OPEN_PDF);
            }
        });

        rootView.findViewById(R.id.discard).setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View view) {
                if(mAdapter.getItemCount() >= 0){
                    View currentView = pagerSnapHelper.findSnapView(layoutManager);
                    if(currentView == null) return;
                    adapterPosition = layoutManager.getPosition(currentView);

                    AlertDialog.Builder alert = new AlertDialog.Builder(getContext(), R.style.AlertDialogCustom);

                    TextView textView = new TextView(getContext());
                    textView.setText("Delete current image");
                    textView.setPadding(20, 30, 20, 30);
                    textView.setTextSize(20F);
                    textView.setTextColor(Color.BLACK);
                    alert.setCustomTitle(textView);

                    alert.setPositiveButton("Delete", new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int whichButton) {
                            imageEditFragmentCallback.editDeleteImageCallback(adapterPosition);
                        }
                    });

                    alert.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int whichButton) {
                            // what ever you want to do with No option.
                        }
                    });

                    alert.show();
                }
            }
        });

        HorizontalScrollView filter_scroll_view = rootView.findViewById(R.id.filter_scroll_view);
        ImageView original_filter_view = rootView.findViewById(R.id.original_filter);
        ImageView magic_filter_view = rootView.findViewById(R.id.magic_filter);
        ImageView sharpen_filter_view = rootView.findViewById(R.id.sharpen_filter);
        ImageView gray_filter_view = rootView.findViewById(R.id.gray_filter);
        ImageView dark_magic_filter_view = rootView.findViewById(R.id.dark_magic_filter);
        LinearLayout imageEffectsBtns = rootView.findViewById(R.id.imageEffectsBtns);
        SeekBar brightnessBar = rootView.findViewById(R.id.brightness_bar);
        LinearLayout barContainer = rootView.findViewById(R.id.barContainer);
        FrameLayout imageEffectsView = rootView.findViewById(R.id.imageEffects);

        rootView.findViewById(R.id.brightness_and_contrast).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (bacVisible) {
                    bacVisible = false;
                    imageEffectsView.setVisibility(View.GONE);
                    barContainer.setVisibility(View.GONE);
                } else {
                    if (filterVisible) {
                        filterVisible = false;
                        filter_scroll_view.setVisibility(View.GONE);
                    }
                    imageEffectsView.setVisibility(View.VISIBLE);
                    imageEffectsBtns.setVisibility(View.VISIBLE);
                    barContainer.setVisibility(View.GONE);
                    bacVisible = true;
                    mAdapter.notifyDataSetChanged();
                }
            }
        });

        rootView.findViewById(R.id.brightness_button).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                imageEffectSelected = BRIGHTNESS;
                imageEffectsBtns.setVisibility(View.GONE);
                barContainer.setVisibility(View.VISIBLE);
                View currentView = pagerSnapHelper.findSnapView(layoutManager);
                if(currentView == null) return;
                adapterPosition = layoutManager.getPosition(currentView);
                float beta = (float) documentAndImageInfo.getImages().get(adapterPosition).getBeta();
                brightnessBar.setProgress ((int) (beta * 100.0f));
            }
        });

        rootView.findViewById(R.id.contrast_button).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                imageEffectSelected = CONTRAST;
                imageEffectsBtns.setVisibility(View.GONE);
                barContainer.setVisibility(View.VISIBLE);
                View currentView = pagerSnapHelper.findSnapView(layoutManager);
                if(currentView == null) return;
                adapterPosition = layoutManager.getPosition(currentView);
                float alpha = (float) documentAndImageInfo.getImages().get(adapterPosition).getAlpha();
                brightnessBar.setProgress ((int) ((alpha - 2.0) * 50.0f));
            }
        });

        rootView.findViewById(R.id.imageEffectCheck).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                barContainer.setVisibility(View.GONE);
                imageEffectsView.setVisibility(View.GONE);
                mAdapter.notifyDataSetChanged();
                bacVisible = false;
            }
        });

        rootView.findViewById(R.id.imageEffectReset).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (imageEffectSelected == CONTRAST) {
                    brightnessBar.setProgress(-50);
                } else if (imageEffectSelected == BRIGHTNESS) {
                    brightnessBar.setProgress(0);
                } else {
                    Log.e("Brightnsd", "contrast and brightness both not set, reset.");
                    brightnessBar.setProgress(0);
                }
            }
        });

        filterContainer.setOnFocusChangeListener((view, b) -> {
            Log.d(TAG, "HEre foucus"+String.valueOf(b));
            if(!b) filterContainer.setVisibility(View.INVISIBLE);
        });

        rootView.findViewById(R.id.filters).setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View view) {
                if (filterVisible) {
                    filterVisible = false;
                    filter_scroll_view.setVisibility(View.GONE);
                } else {
                    if (bacVisible) {
                        bacVisible = false;
                        imageEffectsView.setVisibility(View.GONE);
                        barContainer.setVisibility(View.GONE);
                    }
                    filter_scroll_view.setVisibility(View.VISIBLE);
                    View currentView = pagerSnapHelper.findSnapView(layoutManager);
                    if(currentView == null) return;

                    adapterPosition = layoutManager.getPosition(currentView);
                    Picasso.get().load(documentAndImageInfo.getImages().get(adapterPosition).getUri())
                            .transform(new FilterTransformation("original_filter"))
                            .fit()
                            .centerCrop()
                            .into(original_filter_view);
                    Picasso.get().load(documentAndImageInfo.getImages().get(adapterPosition).getUri())
                            .transform(new FilterTransformation("magic_filter"))
                            .fit()
                            .centerCrop()
                            .into(magic_filter_view);
                    Picasso.get().load(documentAndImageInfo.getImages().get(adapterPosition).getUri())
                            .transform(new FilterTransformation("sharpen_filter"))
                            .fit()
                            .centerCrop()
                            .into(sharpen_filter_view);
                    Picasso.get().load(documentAndImageInfo.getImages().get(adapterPosition).getUri())
                            .transform(new FilterTransformation("dark_magic_filter"))
                            .fit()
                            .centerCrop()
                            .into(dark_magic_filter_view);
                    Picasso.get().load(documentAndImageInfo.getImages().get(adapterPosition).getUri())
                            .transform(new FilterTransformation("gray_filter"))
                            .fit()
                            .centerCrop()
                            .into(gray_filter_view);
                    filterVisible = true;
                }
            }
        });

        original_filter_view.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View view) {
                View currentView = pagerSnapHelper.findSnapView(layoutManager);
                if(currentView == null) return;
                adapterPosition = layoutManager.getPosition(currentView);
                documentAndImageInfo.getImages().get(adapterPosition).setFilterId(
                        ImageEditUtil.getFilterId("original_filter"));
                mAdapter.notifyDataSetChanged();
            }
        });

        magic_filter_view.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View view) {
                View currentView = pagerSnapHelper.findSnapView(layoutManager);
                if(currentView == null) return;
                adapterPosition = layoutManager.getPosition(currentView);
                documentAndImageInfo.getImages().get(adapterPosition).setFilterId(
                        ImageEditUtil.getFilterId("magic_filter"));
                mAdapter.notifyDataSetChanged();
            }
        });

        sharpen_filter_view.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View view) {
                View currentView = pagerSnapHelper.findSnapView(layoutManager);
                if(currentView == null) return;
                adapterPosition = layoutManager.getPosition(currentView);
                documentAndImageInfo.getImages().get(adapterPosition).setFilterId(
                        ImageEditUtil.getFilterId("sharpen_filter"));
                mAdapter.notifyDataSetChanged();
            }
        });

        gray_filter_view.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View view) {
                View currentView = pagerSnapHelper.findSnapView(layoutManager);
                if(currentView == null) return;
                adapterPosition = layoutManager.getPosition(currentView);
                documentAndImageInfo.getImages().get(adapterPosition).setFilterId(
                        ImageEditUtil.getFilterId("gray_filter"));
                mAdapter.notifyDataSetChanged();
            }
        });

        dark_magic_filter_view.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View view) {
                View currentView = pagerSnapHelper.findSnapView(layoutManager);
                if(currentView == null) return;
                adapterPosition = layoutManager.getPosition(currentView);
                documentAndImageInfo.getImages().get(adapterPosition).setFilterId(ImageEditUtil.getFilterId("dark_magic_filter"));
                mAdapter.notifyDataSetChanged();
            }
        });

        rootView.findViewById(R.id.rotate).setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View view) {
                View currentView = pagerSnapHelper.findSnapView(layoutManager);
                if(currentView == null) return;
                adapterPosition = layoutManager.getPosition(currentView);
                documentAndImageInfo.getImages().get(adapterPosition).incrementRotationConfig();
                mAdapter.notifyDataSetChanged();
            }
        });

        Observable.create(s->{
            brightnessBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int i, boolean b) {
                    s.onNext(i);
                }
                @Override
                public void onStartTrackingTouch(SeekBar seekBar) {}
                @Override
                public void onStopTrackingTouch(SeekBar seekBar) {}
            });
        })
        .throttleLatest(100, TimeUnit.MILLISECONDS)
        .subscribeOn(AndroidSchedulers.mainThread())
        .observeOn(AndroidSchedulers.mainThread())
        .subscribe(pos->{
            int i = (int) pos;
            View currentView = pagerSnapHelper.findSnapView(layoutManager);
            if(currentView == null) return;
            adapterPosition = layoutManager.getPosition(currentView);

            if (imageEffectSelected == CONTRAST) {
                effectVal = (float)(i+100)/100.0f * 2;
                documentAndImageInfo.getImages().get(adapterPosition).setAlpha(effectVal);
            } else if (imageEffectSelected == BRIGHTNESS) {
                effectVal = i / 100.0f;
                documentAndImageInfo.getImages().get(adapterPosition).setBeta(effectVal);
            } else {
                effectVal = 1.0f;
                Log.e("Brightnsd", "contrast and brightness both not set.");
            }

            ImageInfo imageInfo = documentAndImageInfo.getImages().get(adapterPosition);
            ImageView temp = currentView.findViewById(R.id.image_edit_item);

            Target target = new Target() {
                @Override
                public void onBitmapLoaded(Bitmap bmp1, Picasso.LoadedFrom from) {
                    Bitmap newBitmap;
                    ContrastFilterTransformation1 t1 = new ContrastFilterTransformation1(getContext(), (float) imageInfo.getAlpha());
                    BrightnessFilterTransformation1 t2 = new BrightnessFilterTransformation1(getContext(), (float) imageInfo.getBeta());
                    newBitmap = t1.transform (bmp1);
                    newBitmap = t2.transform (newBitmap);
                    temp.setImageBitmap(newBitmap);
                    Log.d("Brightnsd", String.valueOf(i));
                }

                @Override
                public void onBitmapFailed(Exception e, Drawable errorDrawable) {

                }

                @Override
                public void onPrepareLoad(Drawable placeHolderDrawable) {

                }
            };
            temp.setTag(target);
            int size = (int) Math.ceil(Math.sqrt(RecyclerViewEditAdapter.MAX_WIDTH * RecyclerViewEditAdapter.MAX_HEIGHT));

            Picasso.get().load(imageInfo.getUri())
                    .transform(new CropTransformation(imageInfo.getCropPositionMap(), imageInfo.getRotationConfig()))
                    .transform(new FilterTransformation(ImageEditUtil.getFilterName(imageInfo.getFilterId())))
                    .resize(size, size)
                    .centerInside()
                    .rotate(imageInfo.getRotationConfig())
                    .into(target);

        });

        ImageButton btnMainCrop = rootView.findViewById(R.id.crop);
        Button btnCropApply = rootView.findViewById(R.id.crop_apply);
        Button btnCropBack = rootView.findViewById(R.id.crop_back);
        Button btnCropAutoDetect = rootView.findViewById(R.id.crop_auto_detect);
        Button btnCropNoCrop = rootView.findViewById(R.id.crop_no_crop);
        Button btnCropRotate = rootView.findViewById(R.id.crop_rotate);

        btnMainCrop.setOnClickListener(mainCrop);
        btnCropApply.setOnClickListener(cropApply);
        btnCropBack.setOnClickListener(cropBack);
        btnCropAutoDetect.setOnClickListener(cropAutoDetect);
        btnCropNoCrop.setOnClickListener(cropNoCrop);
        btnCropRotate.setOnClickListener(cropRotate);

        imageEditFragmentCallback.onCreateEditCallback();

        //recyclerView.smoothScrollToPosition(newAdapterPosition);

        return rootView;
    }

    public void setImagePathList(DocumentAndImageInfo documentAndImageInfo) {
        this.documentAndImageInfo = documentAndImageInfo;
        /*
        On rotating the device (auto rotate)

        16781-16781/com.example.scanin E/AndroidRuntime: FATAL EXCEPTION: main
        Process: com.example.scanin, PID: 16781
        java.lang.NullPointerException: Attempt to invoke virtual method
        'void com.example.scanin.RecyclerViewEditAdapter.setmDataset
        (com.example.scanin.DatabaseModule.DocumentAndImageInfo)' on a null object reference

        Possible solutions -
            enforce portrait mode.
            Check onPause, onRestart in ScanActivity
         */
        mAdapter.setmDataset(documentAndImageInfo);
    }

    public void setCurrentAdapterPosition(Integer position){
        newAdapterPosition = position;
    }

    public void setCurrentMachineState(int currentMachineState) {
        this.CurrentMachineState = currentMachineState;
    }

    @Override
    public void onDestroyView() {
        mAdapter.notifyDataSetChanged();
        super.onDestroyView();
        disposable.clear();
    }

    public void saveInGalleryHelper (ImageInfo imageInfo, String new_name_edited) throws Exception {
        currentImg = new ImageData(imageInfo);
        currentImg.setOriginalBitmap(getContext());

        int width = currentImg.getOriginalWidth();
        int height = currentImg.getOriginalHeight();

        currentImg.setOriginalBitmap(rotateBitmap(currentImg.getOriginalBitmap(),
                90.0f * imageInfo.getRotationConfig()));

        // comes indirectly from database.
        ArrayList <Point> points = currentImg.getCropPosition();

        // if nothing in database
        if (points == null) {
            points = convertMap2ArrayList(getDefaultPoints(width, height));
            //currentImg.setCropPosition(points);
            // if database has cropPoints in orig config. Convert to the current rotation config.
        } else {
            double scale = currentImg.getScale(width, height);
            points = scalePoints(points, (float) scale);
            int rotValue = 0;
            int rotationConfig = currentImg.getRotationConfig();
            while (rotValue != rotationConfig) {
                points = rotateCropPoints(points, width, height, rotValue);
                rotValue = (rotValue + 1) % 4;
            }
            //currentImg.setCropPosition(points);
        }

        currentImg.setOriginalBitmap(ImageData.applyCropImage(currentImg.getOriginalBitmap(),
                points));
        Log.d ("imageSave", "Filter = " + currentImg.getFilterName());
        currentImg.setOriginalBitmap(ImageData.applyFilter(currentImg.getOriginalBitmap(), currentImg.getFilterName()));
        currentImg.setOriginalBitmap(ImageData.changeContrastAndBrightness(getContext(),
                currentImg.getOriginalBitmap(), (float) imageInfo.getAlpha(),
                (float) imageInfo.getBeta()));

//        File photoFile = new File (getContext().getExternalFilesDir(Environment.DIRECTORY_PICTURES).getAbsolutePath(), new_name_edited);
//        photoFile.createNewFile();
//        FileUtils.saveFile(photoFile, currentImg.getOriginalBitmap());
        FileUtils.saveImage(getContext(), currentImg.getOriginalBitmap(), new_name_edited);
    }

}