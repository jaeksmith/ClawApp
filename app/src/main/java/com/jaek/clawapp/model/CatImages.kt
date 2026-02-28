package com.jaek.clawapp.model

import com.jaek.clawapp.R

object CatImages {
    fun getDrawableRes(catName: String): Int? = when (
        catName.lowercase().replace(" ", "").replace("gentleman", "gentleman")
    ) {
        "ty"                    -> R.drawable.cat_ty
        "gentlemanmustachios"   -> R.drawable.cat_gentlemanmustachios
        "nocci"                 -> R.drawable.cat_nocci
        "nommy"                 -> R.drawable.cat_nommy
        "smoresy"               -> R.drawable.cat_smoresy
        "cay"                   -> R.drawable.cat_cay
        else                    -> null
    }
}
