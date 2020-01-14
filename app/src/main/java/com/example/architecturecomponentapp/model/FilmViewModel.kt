package com.example.architecturecomponentapp.model

import android.app.Application
import android.util.Log.e
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData

import kotlinx.coroutines.*

import com.example.architecturecomponentapp.data.dao.FilmDao
import com.example.architecturecomponentapp.data.database.remote.FilmsApi
import com.example.architecturecomponentapp.data.entity.FilmData
import com.example.architecturecomponentapp.util.FilmApiStatus
import com.squareup.moshi.JsonDataException

class FilmViewModel (private val databaseDao: FilmDao, app: Application) : AndroidViewModel (app) {

    // Coroutines
    private var viewModelJob = Job()
    private val uiCoroutineScope = CoroutineScope(Dispatchers.Main + viewModelJob)
    // dados do filme da API.
    private var _requestFilm = MutableLiveData<FilmsJson.FilmJson>()
    // adaptar resultado para FilmData
    val responseFilmJson: LiveData<FilmsJson.FilmJson> get() = _requestFilm
    // lista de filmes da API
    private var _requestFilmList = MutableLiveData<FilmsJson>()
    val responseFilmList: LiveData<FilmsJson> get() = _requestFilmList
    // lista de generos existentes na API
    private var listGenre: Array<Genres.Genre>? = null
    // connection status
    private val _status = MutableLiveData<FilmApiStatus>()
    // get() connection  status
    val status: LiveData<FilmApiStatus> get() = _status
    // pagina atual
    private var _actualPage: Long = 1
    val actualPage: Long get() = _actualPage
    // total de paginas
    private var _totalPages: Long = 0
    val totalPages: Long get() = _totalPages
    // query da pagina da API
    private var _query: String = ""
    val query: String get() = _query

    /**
     *  filmsDatabase tem acesso a lista de filmes do database local.
     *  filmsDatabase deve ser usado para qualquer acesso a esse database local de filmes,
     *  por parte de qualquer fragments ou activities atraves do seu get().
     *  mementoPresentationFilmList foi criado para manter uma copia do estado atual do
     *  database de filmes. O database local de filmes retona um LiveData da lista de filmes, que so pode
     *  pode ser acessado atraves do observador. sempre que o observador sinalizar uma modificacao
     *  nesse database, ele deve repassar o "it" referente a essa lista de filmes, para que o tanto
     *  o memento, quanto o proprio presentationFilmList sejam atualizados. A principal funcao do
     *  memento aqui e manter uma copia da lista de filmes do database mesmo apos uma busca, sendo
     *  assim, caso o presentationFilmList precise reapresentar a lista do database, ele possa fazer
     *  isso realizando uma copia do memento.
     *  presentationFilmList deve apenas apresentar a lista de filmes do database na tela
     *  pelo ViewHolder, seja atraves do proprio "it" referente a lista de filmes database, ou
     *  pela newList criada atraves do filtro de pesquisa, que recebe essa newLista apos ser
     *  realizado esse filtro nos filmes do database, atraves do mementoPresentationFilmList.
     */
    // lista de filmes direto do database
    private var _filmsDatabase: LiveData<List<FilmData>>? = databaseDao.filmList()
    // get do database
    val filmsDataBase: LiveData<List<FilmData>>? get() = _filmsDatabase
    // mantem o estado atual do database para fazer buscas baseado no database, e nao na lista de apresentacao
    private var mementoPresentationFilmList: MutableLiveData<List<FilmData>>? = MutableLiveData()
    // a lista de apresentacao pode ser modificada baseado na busca, o memento so se altera apos alteracao no database
    var presentationFilmList: MutableLiveData<List<FilmData>>? = MutableLiveData()

    init {
        uiCoroutineScope.launch {
            val request= FilmsApi.retrofitService.callGenreMovieApi()
            listGenre = try {
                request.await().genreList
            } catch (t: Throwable) {
                e("ERRO - FILM REQUEST", t.message!!)
                emptyArray()
            }
        }
    }

    // recupera apenas um unico filme da API, para exibir suas informacoes na activity de detalhes
    fun requestFilmApiService (filmId: Long) {
        runBlocking {
            //receber a chamada da API sem bloquear a thread princial
            val getCallDeferred = FilmsApi.retrofitService.callFilmApi(filmId)
            try {
                val requestResult = getCallDeferred.await()
                //atualizando valor do _requestFilm
                _requestFilm.value = requestResult
            }
            catch (t: Throwable) {
                e("ERRO - FILM REQUEST", t.message!!)
                _requestFilm.value = FilmsJson.FilmJson(genres = arrayOf(), productionCompanies = arrayOf())
            }
        }
    }

    // recupera uma lista de filmes da API
    fun requestFilmListApiService (page: Long = _actualPage) {
        /*
        *   atualizar a query: caso o usuario tenha enviado uma, a query sera atualizada para a
        *   nova query requisitada. quando o usuario apenas cancela uma pesquisa, ou a consulta
        *   vem da pagina inicial, o query = ""
        */
        if (_query == "") {
            requestPopularFilmList(page)
        }
        else {
            requestSearchedFilmList(page)
        }
    }

    // recuperar a lista de filmes mais populares da API
    private fun requestPopularFilmList(page: Long) {
        //receber a chamada da API sem bloquear a thread princial
        uiCoroutineScope.launch {
            try {
                val getCallDeferred = FilmsApi.retrofitService.callPopularMovieListApi(page)
                _status.value = FilmApiStatus.LOADING
                val resultList = getCallDeferred.await()
                _requestFilmList.value = resultList
                // informacoes sobre numero de paginas
                _actualPage = resultList.page
                _totalPages = resultList.totalPages
                // normalizar status da requisicao
                _status.value = FilmApiStatus.DONE
            }
            catch (t: Throwable) {
                e("ERRO - REQUEST", t.message!!)
                Toast.makeText(getApplication(), "ERRO: não foi possível recuperar lista de filmes.",Toast.LENGTH_LONG).show()
                _status.value = FilmApiStatus.ERRO
                _requestFilmList.value = FilmsJson( emptyArray( ) )
            }
        }
    }

    // recupera uma lista de filmes da API baseado na busca do usuario
    private fun requestSearchedFilmList (page: Long = _actualPage) {
        uiCoroutineScope.launch {
            //receber a chamada da API sem bloquear a thread princial
            val getCallDeferred = FilmsApi.retrofitService.callSearchMovieList(page, query)
            try {
                _status.value = FilmApiStatus.LOADING
                // pegar lista de filmes da API pela busca
                val resultList = getCallDeferred.await()
                _requestFilmList.value = resultList
                // informacoes sobre numero de paginas
                _actualPage = resultList.page
                _totalPages = resultList.totalPages
                // normalizar status da requisicao
                _status.value = FilmApiStatus.DONE

                if (resultList.movies?.size == 0)
                    Toast.makeText(getApplication(), "A busca não retornou nenhum filme.",Toast.LENGTH_LONG).show()
                else
                    Toast.makeText(getApplication(), "A busca retornou com sucesso.",Toast.LENGTH_LONG).show()

            }
            catch(t: JsonDataException) {
                e("ERRO - search REQUEST", t.message!!)
                Toast.makeText(getApplication(), "ERRO: problema com os dados dos filmes recuperados.",Toast.LENGTH_LONG).show()
                _status.value = FilmApiStatus.ERRO
                _requestFilmList.value = FilmsJson( emptyArray( ) )
            }
            catch (t: Throwable) {
                e("ERRO - search REQUEST", t.message!!)
                Toast.makeText(getApplication(), "ERRO: não foi possível buscar por filme pesquisado.",Toast.LENGTH_LONG).show()
                _status.value = FilmApiStatus.ERRO
                _requestFilmList.value = FilmsJson( emptyArray( ) )
            }
        }
    }

    fun setSearch (query: String = "") {
        // se nao receber consulta, entao aplica consulta limpa.
        _query = query
    }

    fun loadFilmDatabase () {
        presentationFilmList?.value = mementoPresentationFilmList?.value
    }

    // armazenar item presentes no database em lista multaveis para apresentacao de dados
    fun setPresentationDatabase(it: List<FilmData>) {
        uiCoroutineScope.launch {
            // memento para backup do estado atual do database e presentation para viewholder
            mementoPresentationFilmList?.postValue( it )
            presentationFilmList?.postValue( it )
        }
    }

    // buscar filmes no database local
    fun searchFilmDatabase (query: String) {
        uiCoroutineScope.launch {
            // o memento deve ser verificado para ver se esta nulo
            mementoPresentationFilmList?.value?.let {
                // caso nao esteja vazio, criaremos um vetor que ira criar uma nova lista de apresentacao
                val newList: MutableList<FilmData>? = mutableListOf()
                // se contem parte ou toda a palavra deve adicionar a nova lista
                for (i in 0 until it.size) {
                    if ( it[i].title.toLowerCase().contains(query.toLowerCase()) )
                        newList?.add(it[i])
                    // lista de apresentacao recebe a nova lista que foi buscada
                }
                presentationFilmList?.value = newList
            }
        }
    }

    // inserir filme chamando funcao de suspensao
    fun insertFilm(
        id: Long,
        title: String,
        releaseDate: String,
        genresString: String,
        homepage: String,
        originalLanguage: String,
        overview: String,
        popularity: String,
        posterPath: String,
        status:String,
        revenue: Long,
        budget: Long,
        runtime: Int,
        voteAverage: Float,
        companiesString: String)
    {
        uiCoroutineScope.launch {
            insert( FilmData(
                id = id,
                title = title,
                releaseDate = releaseDate,
                genres = genresString,
                homepage = homepage,
                originalLanguage = originalLanguage,
                overview = overview,
                popularity = popularity,
                posterPath = posterPath,
                status = status,
                revenue = revenue,
                budget = budget,
                runtime = runtime,
                voteAverage = voteAverage,
                productionCompanies = companiesString
            ) )
        }
    }

    // inserir film chamando função de suspensao
    fun insertFilm(filmData: FilmData) {
        uiCoroutineScope.launch {
            insert(filmData)
        }
    }

    //funcao de suspensao para inserir filmData no BD
    private suspend fun insert (filmData: FilmData) {
        withContext(Dispatchers.IO) { databaseDao.insertFilm(filmData) }
    }

    // deletar film chamando função de suspensao
    fun deleteFilm(filmData: FilmData) {
        uiCoroutineScope.launch {
            delete(filmData)
        }
    }

    private suspend fun delete(film: FilmData) {
        withContext(Dispatchers.IO) { databaseDao.deleteFilm(film) }
    }

    // metodo para limpar o DB
    fun clearDatabase () {
        uiCoroutineScope.launch { withContext(Dispatchers.IO) { databaseDao.clear() } }
    }

    // retornar film buscado pelo ID
    fun getFilm (id: Long): FilmData {
        return runBlocking {
            withContext(Dispatchers.IO) {
                databaseDao.get(id)
            }
        }
    }

    // verificar se o filme esta no database
    fun isFavoriteFilm (id: Long) : Boolean {
        var result = false
        runBlocking {
            withContext(Dispatchers.IO) {
                result = databaseDao.getFavorite(id)
            }
        }
        return result
    }

    override fun onCleared() {
        super.onCleared()
        // cancelar raiz das coroutines
        viewModelJob.cancel()
    }
}