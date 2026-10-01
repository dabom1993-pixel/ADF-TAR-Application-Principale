package fr.adftar.principale

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.GridView

/**
 * Grille qui prend la hauteur de toutes ses tuiles au lieu de défiler elle-même :
 * les applications et les versions bêta s'affichent ainsi l'une sous l'autre.
 */
class GrilleComplete(context: Context, attrs: AttributeSet?) : GridView(context, attrs) {
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val hauteurIllimitee = View.MeasureSpec.makeMeasureSpec(Int.MAX_VALUE shr 2, View.MeasureSpec.AT_MOST)
        super.onMeasure(widthMeasureSpec, hauteurIllimitee)
    }
}
