package android.content;

/** Real Android interface; AlertDialog's builder takes this type. */
public interface DialogInterface {
    int BUTTON_POSITIVE = -1;
    int BUTTON_NEGATIVE = -2;
    int BUTTON_NEUTRAL = -3;

    void cancel();

    void dismiss();

    interface OnClickListener {
        void onClick(DialogInterface dialog, int which);
    }

    interface OnDismissListener {
        void onDismiss(DialogInterface dialog);
    }

    interface OnCancelListener {
        void onCancel(DialogInterface dialog);
    }
}
