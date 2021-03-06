package com.czbix.v2ex.ui.fragment

import android.content.Intent
import android.os.Bundle
import android.support.v4.app.Fragment
import android.support.v4.app.LoaderManager.LoaderCallbacks
import android.support.v4.content.Loader
import android.support.v4.widget.SwipeRefreshLayout
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.view.*
import com.crashlytics.android.Crashlytics
import com.czbix.v2ex.AppCtx
import com.czbix.v2ex.R
import com.czbix.v2ex.common.UserState
import com.czbix.v2ex.common.exception.ConnectionException
import com.czbix.v2ex.common.exception.FatalException
import com.czbix.v2ex.common.exception.RemoteException
import com.czbix.v2ex.dao.NodeDao
import com.czbix.v2ex.event.BaseEvent
import com.czbix.v2ex.helper.RxBus
import com.czbix.v2ex.model.Node
import com.czbix.v2ex.model.Page
import com.czbix.v2ex.model.Topic
import com.czbix.v2ex.network.RequestHelper
import com.czbix.v2ex.ui.MainActivity
import com.czbix.v2ex.ui.TopicActivity
import com.czbix.v2ex.ui.TopicEditActivity
import com.czbix.v2ex.ui.adapter.TopicAdapter
import com.czbix.v2ex.ui.loader.AsyncTaskLoader.LoaderResult
import com.czbix.v2ex.ui.loader.TopicListLoader
import com.czbix.v2ex.ui.widget.DividerItemDecoration
import com.czbix.v2ex.ui.widget.TopicView.OnTopicActionListener
import com.czbix.v2ex.util.*
import rx.Observable
import rx.Subscription

class TopicListFragment : Fragment(), LoaderCallbacks<LoaderResult<TopicListLoader.TopicList>>, SwipeRefreshLayout.OnRefreshListener, OnTopicActionListener {
    private lateinit var mPage: Page

    private lateinit var mAdapter: TopicAdapter
    private lateinit var mLayout: SwipeRefreshLayout
    private lateinit var mRecyclerView: RecyclerView
    private lateinit var mFavIcon: MenuItem

    private val subscriptions: MutableList<Subscription> = mutableListOf()
    private var mFavored: Boolean = false
    private var mOnceToken: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val arguments = arguments
        if (arguments != null) {
            arguments.getParcelable<Page>(ARG_PAGE).let {
                if (it == null) {
                    throw FatalException("node can't be null")
                }

                mPage = it
            }
        }

        setHasOptionsMenu(true)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        // Inflate the layout for this fragment
        mLayout = inflater.inflate(R.layout.fragment_topic_list,
                container, false) as SwipeRefreshLayout
        mRecyclerView = mLayout.findViewById(R.id.recycle_view) as RecyclerView

        mLayout.setOnRefreshListener(this)
        val layoutManager = LinearLayoutManager(mLayout.context)
        mRecyclerView.layoutManager = layoutManager
        mRecyclerView.addItemDecoration(DividerItemDecoration(activity, DividerItemDecoration.VERTICAL_LIST))

        mAdapter = TopicAdapter(this)
        mRecyclerView.adapter = mAdapter

        mLayout.isRefreshing = true
        return mLayout
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)

        val activity = activity as MainActivity

        val shouldSetTitle: Boolean
        if (mPage is Node) {
            val node = mPage as Node
            if (!node.hasInfo()) {
                NodeDao.get(node.name).let {
                    if (it == null) {
                        return
                    }
                    mPage = node
                }
            }
            shouldSetTitle = true
        } else if (mPage === Page.PAGE_FAV_TOPIC) {
            activity.setNavSelected(R.id.drawer_favorite)
            shouldSetTitle = true
        } else {
            shouldSetTitle = false
        }

        if (shouldSetTitle) {
            activity.title = mPage.title
        }
    }

    override fun onStart() {
        super.onStart()

        val loaderManager = loaderManager
        if (loaderManager.getLoader<Any>(0) != null) {
            // already loaded
            return
        }
        loaderManager.initLoader(0, null, this)
    }

    override fun onStop() {
        super.onStop()

        AppCtx.eventBus.unregister(this)
    }

    override fun onCreateLoader(id: Int, args: Bundle?): Loader<LoaderResult<TopicListLoader.TopicList>> {
        val log = String.format("load list: %s", mPage.title)
        Crashlytics.log(log)
        LogUtils.d(TAG, log)

        return TopicListLoader(activity, mPage)
    }

    override fun onLoadFinished(loader: Loader<LoaderResult<TopicListLoader.TopicList>>, result: LoaderResult<TopicListLoader.TopicList>) {
        mLayout.isRefreshing = false
        if (result.hasException()) {
            ExceptionUtils.handleExceptionNoCatch(this, result.mException)
            return
        }

        result.mResult.let {
            mFavored = it.isFavorited
            mOnceToken = it.onceToken
            mAdapter.setDataSource(result.mResult)
        }

        activity.invalidateOptionsMenu()
    }

    override fun onLoaderReset(loader: Loader<LoaderResult<TopicListLoader.TopicList>>) {
        mAdapter.setDataSource(null)
    }

    override fun onRefresh() {
        val loader = loaderManager.getLoader<Any>(0) ?: return
        loader.forceLoad()

        mRecyclerView.smoothScrollToPosition(0)
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.menu_topic_list, menu)

        if (UserState.isLoggedIn()) {
            mFavIcon = menu.findItem(R.id.action_fav)

            updateFavIcon()
        } else {
            menu.findItem(R.id.action_new_topic).isVisible = false
            menu.findItem(R.id.action_fav).isVisible = false
        }

        super.onCreateOptionsMenu(menu, inflater)
    }

    private fun updateFavIcon() {
        if (mPage !is Node || mOnceToken == null) {
            mFavIcon.isVisible = false
            return
        }

        val icon = if (mFavored)
            R.drawable.ic_favorite_white_24dp else R.drawable.ic_favorite_border_white_24dp

        mFavIcon.setIcon(icon)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.action_refresh -> {
                mLayout.isRefreshing = true
                onRefresh()
                return true
            }
            R.id.action_fav -> {
                onFavNode()
                return true
            }
            R.id.action_new_topic -> {
                val intent = Intent(activity, TopicEditActivity::class.java)
                if (mPage is Node) {
                    intent.putExtra(TopicEditActivity.KEY_NODE, mPage)
                }
                startActivity(intent)
                return true
            }
        }

        return super.onOptionsItemSelected(item)
    }

    fun onFavNode() {
        assert(mPage is Node)

        mFavored = !mFavored
        updateFavIcon()

        RxBus.subscribe<BaseEvent.NodeEvent> {
            updateFavIcon()
        }.let {
            subscriptions += it
        }

        ExecutorUtils.execute {
            try {
                val node = mPage as Node
                RequestHelper.favor(node, mFavored, mOnceToken!!)
            } catch (e: Exception) {
                when (e) {
                    is ConnectionException, is RemoteException -> {
                        LogUtils.w(TAG, "favorite node failed", e)
                        mFavored = !mFavored
                    }
                    else -> throw e
                }
            }

            RxBus.post(BaseEvent.NodeEvent())
        }
    }

    override fun onTopicOpen(view: View, topic: Topic) {
        val intent = Intent(context, TopicActivity::class.java)
        intent.putExtra(TopicActivity.KEY_TOPIC, topic)

        startActivity(intent)

        topic.setHasRead()
    }

    override fun onDestroy() {
        super.onDestroy()

        subscriptions.unsubscribe()
    }

    companion object {
        private val TAG = TopicListFragment::class.java.simpleName
        private val ARG_PAGE = "page"

        /**
         * Use this factory method to create a new instance of
         * this fragment using the provided parameters.

         * @return A new instance of fragment TopicListFragment.
         */
        fun newInstance(page: Page): TopicListFragment {
            val fragment = TopicListFragment()
            val args = Bundle()
            args.putParcelable(ARG_PAGE, page)
            fragment.arguments = args
            return fragment
        }
    }
}
