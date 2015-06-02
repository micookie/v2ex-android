package com.czbix.v2ex.ui;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.support.annotation.IdRes;
import android.support.design.widget.AppBarLayout;
import android.support.design.widget.NavigationView;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import com.bumptech.glide.Glide;
import com.czbix.v2ex.AppCtx;
import com.czbix.v2ex.R;
import com.czbix.v2ex.eventbus.BusEvent;
import com.czbix.v2ex.model.Avatar;
import com.czbix.v2ex.model.Node;
import com.czbix.v2ex.model.Tab;
import com.czbix.v2ex.model.Topic;
import com.czbix.v2ex.ui.fragment.NodeListFragment;
import com.czbix.v2ex.ui.fragment.TopicListFragment;
import com.czbix.v2ex.util.UserUtils;
import com.google.common.base.Strings;
import com.google.common.eventbus.Subscribe;


public class MainActivity extends AppCompatActivity implements TopicListFragment.TopicListActionListener,
        NavigationView.OnNavigationItemSelectedListener, NodeListFragment.OnNodeActionListener {
    private static final String TAG = MainActivity.class.getSimpleName();
    private static final String PREF_DRAWER_SHOWED = "drawer_showed";

    private boolean mRegisteredEventBus;
    private TextView mUsername;
    private AppBarLayout mAppBar;
    private DrawerLayout mDrawerLayout;
    private NavigationView mNav;
    private ImageView mAvatar;
    private TopicListFragment mTopicListFragment;
    private NodeListFragment mNodeListFragment;
    private SharedPreferences mPreferences;

    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mPreferences = getPreferences(MODE_PRIVATE);
        mAvatar = ((ImageView) findViewById(R.id.avatar_img));
        mUsername = (TextView) findViewById(R.id.username_tv);
        mAppBar = ((AppBarLayout) findViewById(R.id.appbar));
        mDrawerLayout = (DrawerLayout) findViewById(R.id.layout);
        mNav = ((NavigationView) findViewById(R.id.nav));

        initToolbar();
        updateUsername();
        initNavDrawer();
        if (savedInstanceState == null) {
            addFragmentToView();
        }
    }

    private void initNavDrawer() {
        mNav.setNavigationItemSelectedListener(this);
        if (!mPreferences.getBoolean(PREF_DRAWER_SHOWED, false)) {
            mDrawerLayout.openDrawer(mNav);
            mPreferences.edit().putBoolean(PREF_DRAWER_SHOWED, true).apply();
        }
    }

    @Override
    public boolean onNavigationItemSelected(MenuItem item) {
        if (item.isChecked()) return false;

        switch (item.getItemId()) {
            case R.id.drawer_all:
                switchFragment(getTopicListFragment());
                return true;
            case R.id.drawer_nodes:
                switchFragment(getNodeListFragment());
                return true;
        }

        return false;
    }

    public void setNavSelected(@IdRes int menuId) {
        final Menu menu = mNav.getMenu();
        menu.findItem(menuId).setChecked(true);
    }

    private void switchFragment(Fragment fragment) {
        mDrawerLayout.closeDrawer(mNav);

        getSupportFragmentManager().beginTransaction()
                .replace(R.id.fragment, fragment)
                .setTransition(FragmentTransaction.TRANSIT_FRAGMENT_OPEN)
                .addToBackStack(null)
                .commit();
    }

    private void updateUsername() {
        if (Strings.isNullOrEmpty(AppCtx.getInstance().getUsername())) {
            mAvatar.setVisibility(View.INVISIBLE);
            mUsername.setVisibility(View.INVISIBLE);
            return;
        }

        mAvatar.setVisibility(View.VISIBLE);
        mUsername.setVisibility(View.VISIBLE);
        final Avatar avatar = UserUtils.getAvatar();
        Glide.with(this).load(avatar.getUrlByDp(getResources().getDimension(R.dimen.nav_avatar_size)))
                .crossFade().into(mAvatar);
        mUsername.setText(AppCtx.getInstance().getUsername());
    }

    private void initToolbar() {
        final Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        if (toolbar == null) {
            return;
        }

        setSupportActionBar(toolbar);
    }

    private void addFragmentToView() {
        final FragmentManager fragmentManager = getSupportFragmentManager();
        fragmentManager.beginTransaction()
                .replace(R.id.fragment, getTopicListFragment())
                .commit();
    }

    private TopicListFragment getTopicListFragment() {
        if (mTopicListFragment == null) {
            mTopicListFragment = TopicListFragment.newInstance(Tab.TAB_ALL);
        }

        return mTopicListFragment;
    }

    private NodeListFragment getNodeListFragment() {
        if (mNodeListFragment == null) {
            mNodeListFragment = NodeListFragment.newInstance();
        }

        return mNodeListFragment;
    }

    @Override
    public boolean onCreateOptionsMenu(final Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);

        enableLoginMenu(menu);

        return true;
    }

    private void enableLoginMenu(Menu menu) {
        if (!Strings.isNullOrEmpty(AppCtx.getInstance().getUsername())) {
            return;
        }

        // not sign in yet
        AppCtx.getEventBus().register(this);
        mRegisteredEventBus = true;
        final MenuItem loginMenu = menu.add(R.string.action_sign_in);
        loginMenu.setOnMenuItemClickListener(new MenuItem.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem item) {
                startActivity(new Intent(MainActivity.this, LoginActivity.class));
                return true;
            }
        });
    }

    @Subscribe
    public void onLoginEvent(BusEvent.LoginEvent e) {
        invalidateOptionsMenu();
        updateUsername();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        if (mRegisteredEventBus) {
            AppCtx.getEventBus().unregister(this);
            mRegisteredEventBus = false;
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        switch (id) {
            case android.R.id.home:
                onBackPressed();
                return true;
            case R.id.action_settings:
                startActivity(new Intent(this, SettingsActivity.class));
                return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onBackPressed() {
        if (mDrawerLayout.isDrawerOpen(mNav)) {
            mDrawerLayout.closeDrawer(mNav);
            return;
        }
        super.onBackPressed();
    }

    @Override
    public void onTopicOpen(View view, Topic topic) {
        final Intent intent = new Intent(this, TopicActivity.class);
        intent.putExtra(TopicActivity.KEY_TOPIC, topic);

        startActivity(intent);
    }

    @Override
    public void onNodeClick(Node node) {
        getSupportFragmentManager().beginTransaction()
                .replace(R.id.fragment, TopicListFragment.newInstance(node))
                .setTransition(FragmentTransaction.TRANSIT_FRAGMENT_OPEN)
                .addToBackStack(null)
                .commit();
    }
}
