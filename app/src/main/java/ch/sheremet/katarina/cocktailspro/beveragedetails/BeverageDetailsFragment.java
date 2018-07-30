package ch.sheremet.katarina.cocktailspro.beveragedetails;


import android.app.Activity;
import android.arch.lifecycle.Observer;
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.squareup.picasso.Picasso;

import javax.inject.Inject;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;
import ch.sheremet.katarina.cocktailspro.R;
import ch.sheremet.katarina.cocktailspro.di.BeverageDetailsFragmentComponent;
import ch.sheremet.katarina.cocktailspro.di.BeverageDetailsViewModelModule;
import ch.sheremet.katarina.cocktailspro.di.DaggerBeverageDetailsFragmentComponent;
import ch.sheremet.katarina.cocktailspro.model.Beverage;
import ch.sheremet.katarina.cocktailspro.model.BeverageDetails;
import ch.sheremet.katarina.cocktailspro.model.Ingredients;
import ch.sheremet.katarina.cocktailspro.utils.AppExecutors;

/**
 * A simple {@link Fragment} subclass.
 * Use the {@link BeverageDetailsFragment#newInstance} factory method to
 * create an instance of this fragment.
 */
public class BeverageDetailsFragment extends Fragment {

    private static final String TAG = BeverageDetailsFragment.class.getSimpleName();
    private static final String BEVERAGE_PARAM = "drink_id";
    private static final String FAVOURITE_STATE = "is_favourite_drink";
    private static final String BEVERAGE_DETAILS_STATE = "beverage_details";

    @BindView(R.id.detail_beverage_name_tv)
    TextView mName;
    @BindView(R.id.add_to_favourite_iv)
    ImageView mAddToFavourite;
    @BindView(R.id.details_ingredients_tv)
    TextView mIngredients;
    @BindView(R.id.detail_instructions_tv)
    TextView mInstructions;
    @BindView(R.id.detail_glass_tv)
    TextView mGlassType;
    @BindView(R.id.detail_beverage_thumbnail_iv)
    ImageView mThumbnail;
    @BindView(R.id.detail_category_tv)
    TextView mCategory;
    @BindView(R.id.detail_iba_tv)
    TextView mIba;
    @Inject
    BeverageDetailsViewModel mViewModel;
    private boolean mIsFavourite = false;
    private Beverage mBeverage;
    private BeverageDetails mBeverageDetails;
    private OnDataInteraction mListener;

    public BeverageDetailsFragment() {
        // Required empty public constructor
    }

    /**
     * Use this factory method to create a new instance of
     * this fragment using the provided parameters.
     *
     * @return A new instance of fragment BeverageDetailsFragment.
     */
    public static BeverageDetailsFragment newInstance(@NonNull final Beverage beverage) {
        BeverageDetailsFragment fragment = new BeverageDetailsFragment();
        Bundle bundle = new Bundle();
        bundle.putParcelable(BEVERAGE_PARAM, beverage);
        fragment.setArguments(bundle);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        BeverageDetailsFragmentComponent component = DaggerBeverageDetailsFragmentComponent.builder()
                .beverageDetailsViewModelModule
                        (new BeverageDetailsViewModelModule(getActivity())).build();
        component.injectBeverageDetailsFragment(this);

        Bundle bundle = this.getArguments();
        if (bundle != null) {
            mBeverage = bundle.getParcelable(BEVERAGE_PARAM);
        } else {
            getActivity().finish();
        }
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        if (context instanceof OnDataInteraction) {
            mListener = (OnDataInteraction) context;
        } else {
            throw new RuntimeException(context.toString()
                    + " must implement OnBeverageSelected");
        }
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View view = inflater.inflate(R.layout.fragment_beverage_details, container, false);
        ButterKnife.bind(this, view);

        mListener.showProgressBar();
        if (savedInstanceState == null) {
            AppExecutors.getInstance().diskIO().execute(new Runnable() {
                @Override
                public void run() {
                    mIsFavourite = mViewModel.isBeverageFavourite(mBeverage);
                    Activity activity = getActivity();
                    if (activity != null) {
                        getActivity().runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                setFavouriteButtonBackground(mIsFavourite);
                                if (mIsFavourite) {
                                    initFavouriteBeverageDetails();
                                } else {
                                    initBeverageDetailsFromNetwork();
                                }
                            }
                        });
                    }
                }
            });
        } else {
            mIsFavourite = savedInstanceState.getBoolean(FAVOURITE_STATE);
            setFavouriteButtonBackground(mIsFavourite);
            if (savedInstanceState.containsKey(BEVERAGE_DETAILS_STATE)) {
                mBeverageDetails = savedInstanceState.getParcelable(BEVERAGE_DETAILS_STATE);
                updateUI(mBeverageDetails);
            } else {
                getActivity().finish();
            }
        }
        return view;
    }

    private boolean isOnline() {
        ConnectivityManager cm =
                (ConnectivityManager) getActivity().getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) return false;
        NetworkInfo netInfo = cm.getActiveNetworkInfo();
        return netInfo != null && netInfo.isConnectedOrConnecting();
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean(FAVOURITE_STATE, mIsFavourite);
        outState.putParcelable(BEVERAGE_DETAILS_STATE, mBeverageDetails);
    }

    void initFavouriteBeverageDetails() {
        AppExecutors.getInstance().diskIO().execute(new Runnable() {
            @Override
            public void run() {
                mBeverageDetails = mViewModel.getFavouriteBeverageDetails(mBeverage.getId());
                Activity activity = getActivity();
                if (activity != null) {
                    getActivity().runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            updateUI(mBeverageDetails);
                            Log.d(TAG, "Details fetched from DB: " + mBeverageDetails);
                        }
                    });
                }
            }
        });
    }

    void initBeverageDetailsFromNetwork() {
        if (isOnline()) {
            mViewModel.fetchBeverageByID(mBeverage.getId());
            mViewModel.getBeverageDetails().observe(this, new Observer<BeverageDetails>() {
                @Override
                public void onChanged(@Nullable BeverageDetails beverageDetails) {
                    if (beverageDetails != null) {
                        mViewModel.getBeverageDetails().removeObserver(this);
                        mBeverageDetails = beverageDetails;
                        updateUI(mBeverageDetails);
                        Log.d(TAG, "Details fetched from network");
                    }
                }
            });
        } else {
            mListener.showError(getString(R.string.no_internet_user_message));
        }
    }

    private void updateUI(BeverageDetails beverageDetails) {
        mListener.showData();
        mName.setText(beverageDetails.getName());
        mGlassType.setText(beverageDetails.getGlassType());
        mInstructions.setText(beverageDetails.getInstructions());
        mIba.setText(beverageDetails.getIBA());
        mCategory.setText(beverageDetails.getCategory());

        StringBuilder builder = new StringBuilder();
        for (Ingredients ingredients : beverageDetails.getIngredients()) {
            builder.append(ingredients.getIngredient())
                    .append(": ")
                    .append(ingredients.getMeasure())
                    .append("\n");

        }
        if (!builder.toString().isEmpty()) {
            mIngredients.setText(builder.toString());
        }

        Picasso.get().load(beverageDetails.getThumbnailUrl()).error(R.drawable.vuquyv1468876052)
                .placeholder(R.drawable.vuquyv1468876052).into(mThumbnail);
    }

    @OnClick(R.id.add_to_favourite_iv)
    protected void onFavouriteClick() {
        if (mIsFavourite) {
            mIsFavourite = false;
            mViewModel.deleteBeverageFromFavourite(mBeverage, mBeverageDetails);
            Log.d(TAG, "Remove from favourites");
        } else {
            mIsFavourite = true;
            mViewModel.addBeverageToFavourite(mBeverage, mBeverageDetails);
            Log.d(TAG, "Add to favourites");
        }
        setFavouriteButtonBackground(mIsFavourite);
    }

    private void setFavouriteButtonBackground(boolean isFavourite) {
        if (isFavourite) {
            mAddToFavourite.setImageResource(android.R.drawable.btn_star_big_on);
        } else {
            mAddToFavourite.setImageResource(android.R.drawable.btn_star_big_off);
        }
    }

    /**
     * This interface must be implemented by activities that contain this
     * fragment to allow an interaction in this fragment to be communicated
     * to the activity and potentially other fragments contained in that
     * activity.
     */
    public interface OnDataInteraction {
        void showData();

        void showError(String error);

        void showProgressBar();
    }
}
