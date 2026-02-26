package com.jaek.clawapp.ui

import androidx.lifecycle.ViewModel
import com.jaek.clawapp.model.CatLocation
import com.jaek.clawapp.model.CatState
import com.jaek.clawapp.repository.CatRepository
import kotlinx.coroutines.flow.StateFlow

/**
 * ViewModel for sharing state between screens.
 * Gets populated when the service binds.
 */
class ClawViewModel : ViewModel() {

    private var catRepository: CatRepository? = null

    fun bindRepository(repo: CatRepository) {
        catRepository = repo
    }

    val cats: StateFlow<Map<String, CatState>>?
        get() = catRepository?.cats

    fun getAllCats(): List<CatState> =
        catRepository?.getAllCats() ?: emptyList()

    fun getCat(name: String): CatState? =
        catRepository?.getCat(name)

    fun setCatState(catName: String, location: CatLocation) {
        catRepository?.setCatState(catName, location)
    }
}
