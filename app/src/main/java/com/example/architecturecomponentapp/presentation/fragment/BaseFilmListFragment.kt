package com.example.architecturecomponentapp.presentation.fragment

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.ViewModelProviders
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout

import com.example.architecturecomponentapp.R
import com.example.architecturecomponentapp.data.database.local.FilmDatabase
import com.example.architecturecomponentapp.model.FilmViewModel
import com.example.architecturecomponentapp.model.FilmViewModelFactory
import com.example.architecturecomponentapp.presentation.adapter.FilmListAdapter

/**
 * Classe base para os outros fragments que apresentam lista de filmes usando RecyclerView
 */
open class BaseFilmListFragment : Fragment(), SwipeRefreshLayout.OnRefreshListener {

    protected lateinit var mFilmRecyclerView: RecyclerView
    protected lateinit var mSwipeRefreshLayout: SwipeRefreshLayout

    protected lateinit var filmViewModel: FilmViewModel
    protected lateinit var filmListAdapter: FilmListAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        // Inflate o layout desse fragment
        val view = inflater.inflate(R.layout.fragment_api_film_list, container, false)
        initViews(view)

        // recuperar fonte de dados
        val application = requireNotNull(this.activity).application
        val dataSource = FilmDatabase.getInstance(application).filmDao
        val filmViewModelFactory = FilmViewModelFactory(dataSource, application)
        filmViewModel = ViewModelProviders.of(activity!!, filmViewModelFactory).get(FilmViewModel::class.java)

        // configurando RecyclerView
        val layoutManager: RecyclerView.LayoutManager = LinearLayoutManager(activity)
        mFilmRecyclerView.layoutManager = layoutManager
        mFilmRecyclerView.setHasFixedSize(true)

        return view
    }

    private fun initViews(v: View) {
        mFilmRecyclerView = v.findViewById(R.id.film_list_api_recycle_view)
        mSwipeRefreshLayout = v.findViewById(R.id.film_list_api_layout)
        mSwipeRefreshLayout.setOnRefreshListener(this)
    }

    override fun onRefresh() {
        filmViewModel.loadFilmDatabase()
        mSwipeRefreshLayout.isRefreshing = false
    }

}
