package io.github.nfdz.tomatina.main.view;

import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.design.widget.TabLayout;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentStatePagerAdapter;
import android.support.v4.view.ViewPager;
import android.support.v7.app.AppCompatActivity;

import butterknife.BindView;
import butterknife.ButterKnife;
import io.github.nfdz.tomatina.R;
import io.github.nfdz.tomatina.common.utils.SocialPreferencesUtils;
import io.github.nfdz.tomatina.historical.view.HistoricalFragment;
import io.github.nfdz.tomatina.home.view.HomeFragment;
import io.github.nfdz.tomatina.user.view.UserFragment;

public class MainActivity extends AppCompatActivity {

    @BindView(R.id.main_activity_vp) ViewPager main_activity_vp;
    @BindView(R.id.main_activity_tl) TabLayout main_activity_tl;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        ButterKnife.bind(this);
        setupView();
    }

    private void setupView() {
        setUpViewPager();
    }

    private void setUpViewPager() {
        MainPagerAdapter pagerAdapter = new MainPagerAdapter(getSupportFragmentManager());
        main_activity_vp.setAdapter(pagerAdapter);
        main_activity_tl.setupWithViewPager(main_activity_vp);
    }

    private class MainPagerAdapter extends FragmentStatePagerAdapter {

        MainPagerAdapter(FragmentManager fm) {
            super(fm);
        }

        @Override
        public Fragment getItem(int position) {
            if (SocialPreferencesUtils.isSocialEnabled()) {
                switch (position) {
                    case 0:
                        return HomeFragment.newInstance();
                    case 1:
                        return UserFragment.newInstance();
                    case 2:
                        return HistoricalFragment.newInstance();
                    default:
                        throw new IllegalArgumentException("Invalid tab position: " + position);
                }
            } else {
                switch (position) {
                    case 0:
                        return HomeFragment.newInstance();
                    case 1:
                        return HistoricalFragment.newInstance();
                    default:
                        throw new IllegalArgumentException("Invalid tab position: " + position);
                }
            }
        }

        @Override
        public int getCount() {
            return SocialPreferencesUtils.isSocialEnabled() ? 3 : 2;
        }

        @Nullable
        @Override
        public CharSequence getPageTitle(int position) {
            if (SocialPreferencesUtils.isSocialEnabled()) {
                switch (position) {
                    case 0:
                        return getString(R.string.home_tab);
                    case 1:
                        return getString(R.string.user_tab);
                    case 2:
                        return getString(R.string.historical_tab);
                    default:
                        throw new IllegalArgumentException("Invalid tab position: " + position);
                }
            } else {
                switch (position) {
                    case 0:
                        return getString(R.string.home_tab);
                    case 1:
                        return getString(R.string.historical_tab);
                    default:
                        throw new IllegalArgumentException("Invalid tab position: " + position);
                }
            }
        }

    }

}
