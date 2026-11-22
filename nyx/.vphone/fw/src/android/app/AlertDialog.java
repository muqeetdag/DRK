package android.app;

import android.content.Context;
import android.view.View;
import android.widget.TextView;

/** AlertDialog + Builder (the app styles the returned dialog window). */
public class AlertDialog extends Dialog {

    protected AlertDialog(Context context) { super(context); }

    protected AlertDialog(Context context, int themeResId) { super(context, themeResId); }

    public void setMessage(CharSequence message) {}

    public void setButton(int whichButton, CharSequence text, OnClickListener listener) {}

    public void setView(View view) { setContentView(view); }

    public static final int BUTTON_POSITIVE = -1;
    public static final int BUTTON_NEGATIVE = -2;
    public static final int BUTTON_NEUTRAL = -3;

    public interface OnClickListener {
        void onClick(DialogInterface dialog, int which);
    }

    public interface DialogInterface {
        void cancel();
        void dismiss();
    }

    public static class Builder {

        private final Context context;
        private CharSequence title;
        private CharSequence message;
        private View view;
        private CharSequence positiveText;
        private OnClickListener positiveListener;
        private CharSequence negativeText;
        private OnClickListener negativeListener;
        private boolean cancelable = true;

        public Builder(Context context) { this.context = context; }

        public Builder(Context context, int themeResId) { this.context = context; }

        public Builder setTitle(CharSequence title) { this.title = title; return this; }

        public Builder setTitle(int titleId) { this.title = context.getString(titleId); return this; }

        public Builder setMessage(CharSequence message) { this.message = message; return this; }

        public Builder setMessage(int messageId) { this.message = context.getString(messageId); return this; }

        public Builder setView(View view) { this.view = view; return this; }

        public Builder setView(int layoutResId) { return this; }

        public Builder setCancelable(boolean cancelable) { this.cancelable = cancelable; return this; }

        public Builder setPositiveButton(CharSequence text, OnClickListener listener) {
            this.positiveText = text;
            this.positiveListener = listener;
            return this;
        }

        public Builder setPositiveButton(int textId, OnClickListener listener) {
            return setPositiveButton(context.getString(textId), listener);
        }

        public Builder setNegativeButton(CharSequence text, OnClickListener listener) {
            this.negativeText = text;
            this.negativeListener = listener;
            return this;
        }

        public Builder setNegativeButton(int textId, OnClickListener listener) {
            return setNegativeButton(context.getString(textId), listener);
        }

        public Builder setNeutralButton(CharSequence text, OnClickListener listener) { return this; }

        public Builder setOnDismissListener(OnDismissListener listener) { return this; }

        public Builder setCanceledOnTouchOutside(boolean cancel) { return this; }

        public Builder setIcon(int iconId) { return this; }

        public Builder setItems(CharSequence[] items, OnClickListener listener) { return this; }

        public Builder setSingleChoiceItems(CharSequence[] items, int checkedItem,
                                           OnClickListener listener) { return this; }

        public Builder setMultiChoiceItems(CharSequence[] items, boolean[] checked,
                                          Object listener) { return this; }

        public Builder setAdapter(Object adapter, OnClickListener listener) { return this; }

        public Builder setCustomTitle(View customTitle) { return this; }

        public AlertDialog create() {
            AlertDialog dialog = new AlertDialog(context);
            dialog.setCancelable(cancelable);
            dialog.setTitle(title);
            dialog.setMessage(message);
            TextView body = new TextView(context);
            body.setText(message == null ? "" : message);
            body.setPadding(48, 40, 48, 40);
            dialog.setContentView(body);
            if (view != null) dialog.setView(view);
            dialog.storeButtons(positiveText, negativeText);
            return dialog;
        }

        public AlertDialog show() {
            AlertDialog dialog = create();
            dialog.show();
            return dialog;
        }
    }

    void storeButtons(CharSequence positive, CharSequence negative) {}

    public static class OnDismissListener implements Dialog.OnDismissListener {
        @Override public void onDismiss(Dialog dialog) {}
    }
}
