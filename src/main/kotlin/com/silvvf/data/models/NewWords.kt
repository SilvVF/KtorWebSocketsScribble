package com.silvvf.data.models

import com.silvvf.util.Constants

//can't just send the list as a raw json item if we did this client side it would be difficult to tell what
// the object actually is
data class NewWords(
    val newWords: List<String>
): BaseModel(Constants.TYPE_NEW_WORDS)
