package moe.ono.creator;

import static moe.ono.util.Session.getCurrentChatType;
import static moe.ono.util.Session.getCurrentPeerID;
import static moe.ono.util.analytics.ActionReporter.reportVisitor;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.lxj.xpopup.XPopup;
import com.lxj.xpopup.core.BasePopupView;
import com.lxj.xpopup.core.BottomPopupView;
import com.lxj.xpopup.util.XPopupUtils;

import moe.ono.R;
import moe.ono.hooks.base.util.Toasts;
import moe.ono.hooks.protocol.QFakeFileHelperKt;
import moe.ono.hooks.protocol.QPacketHelperKt;
import moe.ono.ui.CommonContextWrapper;
import moe.ono.util.AppRuntimeHelper;

@SuppressLint("ResourceType")
public class FakeFileSender extends BottomPopupView {
    private static BasePopupView popupView;

    public FakeFileSender(@NonNull Context context) {
        super(context);
    }

    public static void createView(Context context) {
        Context fixContext = CommonContextWrapper.createAppCompatContext(context);
        XPopup.Builder NewPop = new XPopup.Builder(fixContext).moveUpToKeyboard(true).isDestroyOnDismiss(true);
        NewPop.maxHeight((int) (XPopupUtils.getScreenHeight(context) * .7f));
        NewPop.popupHeight((int) (XPopupUtils.getScreenHeight(context) * .63f));


        reportVisitor(AppRuntimeHelper.getAccount(), "CreateView-FakeFileSender");

        popupView = NewPop.asCustom(new FakeFileSender(fixContext));
        popupView.show();
    }

    @SuppressLint("SetTextI18n")
    @Override
    protected void onCreate() {
        super.onCreate();
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            Button btnSend = findViewById(R.id.btn_send);
            EditText filename = findViewById(R.id.file_name);
            EditText filesize = findViewById(R.id.file_size);
            TextView tvTarget = findViewById(R.id.tv_target);

            filename.clearFocus();
            filename.setVisibility(VISIBLE);

            filesize.clearFocus();
            filesize.setVisibility(VISIBLE);

            int chat_type = getCurrentChatType();
            if (chat_type == 1) {
                tvTarget.setText("当前会话: " + getCurrentPeerID() + " | " + "好友");
            } else if (chat_type == 2) {
                tvTarget.setText("当前会话: " + getCurrentPeerID() + " | " + "群聊");
            } else {
                tvTarget.setText("当前会话: " + getCurrentPeerID() + " | " + "未知");
            }

            btnSend.setOnClickListener(v -> {
                String result;
                try {
                    result = QFakeFileHelperKt.create(filename.getText().toString(),
                            QFakeFileHelperKt.parseFileSize(filesize.getText().toString()));
                } catch (Exception e) {
                    Toasts.error(getContext(), "序列化时遇到致命错误，请检查参数");
                    return;
                }

                String send_type = "element";
                if (chat_type == 1) {
                    QPacketHelperKt.sendMessage(result, getCurrentPeerID(), false, send_type, false, "");
                } else if (chat_type == 2) {
                    QPacketHelperKt.sendMessage(result, getCurrentPeerID(), true, send_type, false, "");
                } else {
                    Toasts.error(getContext(), "失败");
                    return;
                }
                Toasts.success(getContext(), "请求成功");
                popupView.dismiss();
            });



        }, 100);


    }




    @Override
    protected void beforeDismiss() {
        super.beforeDismiss();
    }

    @Override
    protected void onDismiss() {
        super.onDismiss();
    }

    @Override
    protected int getImplLayoutId() {
        return R.layout.fakefile_sender_layout;
    }
}


