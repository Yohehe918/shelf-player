package local.shelf.player;
import android.app.AlertDialog;
import android.content.Context;
/** Native dialog behavior with the same quiet appearance/disappearance as Shelf pages. */
final class MotionDialog extends AlertDialog.Builder {
    MotionDialog(Context context){super(context);}
    @Override public AlertDialog create(){
        AlertDialog dialog=super.create();
        if(dialog.getWindow()!=null)dialog.getWindow().setWindowAnimations(Motion.enabled(getContext())?R.style.ShelfDialogMotion:0);
        return dialog;
    }
}
