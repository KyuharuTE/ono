package moe.ono.ui.view;

import android.animation.LayoutTransition;
import android.annotation.SuppressLint;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.style.LeadingMarginSpan;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.ColorInt;
import androidx.annotation.Nullable;
import androidx.core.widget.TextViewCompat;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import moe.ono.R.color;
import moe.ono.R.style;
import moe.ono.R.styleable;
import moe.ono.util.Logger;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Iterator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class JsonViewer extends LinearLayout {
    private final float PADDING = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 16.0F, this.getResources().getDisplayMetrics());
    @ColorInt private int textColorString;
    @ColorInt private int textColorBool;
    @ColorInt private int textColorNull;
    @ColorInt private int textColorNumber;
    @ColorInt private int textColorKey;

    private static Object jsonObject;

    public JsonViewer(Context context) {
        super(context);
        if (this.isInEditMode()) {
            this.initEditMode();
        }
    }

    public JsonViewer(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        if (attrs != null) {
            this.init(context, attrs);
        }
        if (this.isInEditMode()) {
            this.initEditMode();
        }
    }

    public JsonViewer(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        if (attrs != null) {
            this.init(context, attrs);
        }
        if (this.isInEditMode()) {
            this.initEditMode();
        }
    }

    private void init(Context context, @Nullable AttributeSet attrs) {
        TypedArray a = context.getTheme().obtainStyledAttributes(attrs, styleable.JsonViewer, 0, 0);
        Resources r = this.getResources();
        try {
            this.textColorString = a.getColor(styleable.JsonViewer_textColorString, r.getColor(color.jsonViewer_textColorString));
            this.textColorNumber = a.getColor(styleable.JsonViewer_textColorNumber, r.getColor(color.jsonViewer_textColorNumber));
            this.textColorBool = a.getColor(styleable.JsonViewer_textColorBool, r.getColor(color.jsonViewer_textColorBool));
            this.textColorNull = a.getColor(styleable.JsonViewer_textColorNull, r.getColor(color.jsonViewer_textColorNull));
            this.textColorKey = a.getColor(styleable.JsonViewer_textColorKey, r.getColor(color.jsonViewer_textColorKey));
        } finally {
            a.recycle();
        }
    }

    private void initEditMode() {
        String json = "{\"id\":1,\"name\":\"Title\",\"is\":true,\"value\":null,\"array\":[{\"item\":1,\"name\":\"One\"},{\"item\":2,\"name\":\"Two\"}],\"object\":{\"id\":1,\"name\":\"Title\"},\"simple_array\":[1,2,3]}";
        try {
            this.setJson(new JSONObject(json));
        } catch (JSONException e) {
            Logger.e(e);
        }
    }

    public void setJson(Object json) {
        if (!(json instanceof JSONArray) && !(json instanceof JSONObject)) {
            throw new RuntimeException("JsonViewer: JSON must be an instance of org.json.JSONArray or org.json.JSONObject");
        } else {
            super.setOrientation(LinearLayout.VERTICAL);
            this.removeAllViews();
            this.addJsonNode(this, null, json, false);
            jsonObject = json;

        }
    }

    public void setTextColorString(@ColorInt int color) {
        this.textColorString = color;
    }

    public void setTextColorNumber(@ColorInt int color) {
        this.textColorNumber = color;
    }

    public void setTextColorBool(@ColorInt int color) {
        this.textColorBool = color;
    }

    public void setTextColorNull(@ColorInt int color) {
        this.textColorNull = color;
    }

    public void setTextColorKey(@ColorInt int color) {
        this.textColorKey = color;
    }

    public void collapseJson() {
        for (int i = 0; i < this.getChildCount(); ++i) {
            if (this.getChildAt(i) instanceof TextView && this.getChildAt(i + 1) instanceof ViewGroup && this.getChildAt(i + 2) instanceof TextView) {
                this.changeVisibility((ViewGroup) this.getChildAt(i + 1), 0);
                i += 2;
            }
        }
    }

    public void expandJson() {
        this.changeVisibility(this, 8);
    }

    private void changeVisibility(ViewGroup group, int oldVisibility) {
        for (int i = 0; i < group.getChildCount(); ++i) {
            if (group.getChildAt(i) instanceof TextView && group.getChildAt(i + 1) instanceof ViewGroup groupChild && group.getChildAt(i + 2) instanceof TextView) {
                groupChild.setVisibility(oldVisibility);
                group.getChildAt(i).callOnClick();
                this.changeVisibility((ViewGroup) group.getChildAt(i + 1), oldVisibility);
                i += 2;
            }
        }
    }

    /**
     * 递归添加 JSON 节点的视图（header、子容器和 footer）
     */
    private void addJsonNode(LinearLayout content, @Nullable final Object nodeKey, final Object jsonNode, final boolean haveNext) {
        final boolean haveChild = (jsonNode instanceof JSONObject && ((JSONObject) jsonNode).length() != 0)
                || (jsonNode instanceof JSONArray && ((JSONArray) jsonNode).length() != 0);
        final TextView textViewHeader = this.getHeader(nodeKey, jsonNode, haveNext, haveChild);
        content.addView(textViewHeader);

        // 长按 header 弹出对话框，显示当前节点内容（格式化并高亮后），供用户自由复制和选择
        textViewHeader.setOnLongClickListener(new OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                showCopyDialog(jsonNode);
                return true;
            }
        });

        if (haveChild) {
            final ViewGroup viewGroupChild = this.getJsonNodeChild(nodeKey, jsonNode);
            final TextView textViewFooter = this.getFooter(jsonNode, haveNext);
            content.addView(viewGroupChild);
            content.addView(textViewFooter);
            textViewHeader.setOnClickListener(new View.OnClickListener() {
                @SuppressLint("WrongConstant")
                public void onClick(View view) {
                    byte newVisibility;
                    boolean showChild;
                    if (viewGroupChild.getVisibility() == View.VISIBLE) {
                        newVisibility = 8;
                        showChild = false;
                    } else {
                        newVisibility = 0;
                        showChild = true;
                    }
                    textViewHeader.setText(getHeaderText(nodeKey, jsonNode, haveNext, showChild, haveChild));
                    viewGroupChild.setVisibility(newVisibility);
                    textViewFooter.setVisibility(newVisibility);
                }
            });
        }
    }

    private void showCopyDialog(Object jsonNode) {
        final String copyText = getBeautifiedJson(jsonNode);
        CharSequence highlightedText = getHighlightedJson(copyText);

        final TextView textView = new TextView(getContext());
        textView.setText(highlightedText);
        textView.setTextIsSelectable(true);
        int padding = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 16, getResources().getDisplayMetrics());
        textView.setPadding(padding, padding, padding, padding);
        ScrollView scrollView = new ScrollView(getContext());
        scrollView.addView(textView);

        new MaterialAlertDialogBuilder(getContext())
                .setTitle("复制节点内容")
                .setView(scrollView)
                .setPositiveButton("全部复制", (dialog, which) -> {
                    ClipboardManager cm = (ClipboardManager) getContext().getSystemService(Context.CLIPBOARD_SERVICE);
                    ClipData mClipData = ClipData.newPlainText("node", copyText);
                    cm.setPrimaryClip(mClipData);
                })
                .setNegativeButton("关闭", (dialog, which) -> {})
                .show();
    }

    private CharSequence getHighlightedJson(String json) {
        SpannableStringBuilder spannable = new SpannableStringBuilder(json);

        Pattern keyPattern = Pattern.compile("\"([^\"]+)\"(?=\\s*:)");
        Matcher keyMatcher = keyPattern.matcher(json);
        while (keyMatcher.find()) {
            spannable.setSpan(
                    new ForegroundColorSpan(textColorKey),
                    keyMatcher.start(),
                    keyMatcher.end(),
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }

        // 高亮字符串值：匹配 : "value" 的部分（排除键）
        Pattern stringPattern = Pattern.compile(":(\\s*)\"([^\"]*)\"");
        Matcher stringMatcher = stringPattern.matcher(json);
        while (stringMatcher.find()) {
            int start = stringMatcher.start(2) - 1; // 包含左引号
            int end = stringMatcher.end(2) + 1;       // 包含右引号
            spannable.setSpan(
                    new ForegroundColorSpan(textColorString),
                    start,
                    end,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }

        Pattern numberPattern = Pattern.compile(":(\\s*)(-?\\d+(\\.\\d+)?)");
        Matcher numberMatcher = numberPattern.matcher(json);
        while (numberMatcher.find()) {
            int start = numberMatcher.start(2);
            int end = numberMatcher.end(2);
            spannable.setSpan(
                    new ForegroundColorSpan(textColorNumber),
                    start,
                    end,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }

        Pattern boolNullPattern = Pattern.compile(":(\\s*)(true|false|null)");
        Matcher boolNullMatcher = boolNullPattern.matcher(json);
        while (boolNullMatcher.find()) {
            int start = boolNullMatcher.start(2);
            int end = boolNullMatcher.end(2);
            String token = json.substring(start, end);
            int color = "null".equals(token) ? textColorNull : textColorBool;
            spannable.setSpan(
                    new ForegroundColorSpan(color),
                    start,
                    end,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        return spannable;
    }

    private ViewGroup getJsonNodeChild(Object nodeKey, Object jsonNode) {
        LinearLayout content = new LinearLayout(this.getContext());
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding((int) this.PADDING, 0, 0, 0);
        content.setLayoutTransition(new LayoutTransition());
        if (jsonNode instanceof JSONObject object) {
            Iterator<String> iterator = object.keys();
            while (iterator.hasNext()) {
                String key = iterator.next();
                try {
                    this.addJsonNode(content, key, object.get(key), iterator.hasNext());
                } catch (JSONException e) {
                    Logger.e(e);
                }
            }
        } else if (jsonNode instanceof JSONArray object) {
            for (int i = 0; i < object.length(); ++i) {
                try {
                    this.addJsonNode(content, i, object.get(i), i + 1 < object.length());
                } catch (JSONException e) {
                    Logger.e(e);
                }
            }
        }
        return content;
    }

    private TextView getHeader(Object key, @Nullable Object jsonNode, boolean haveNext, boolean haveChild) {
        TextView textView = new TextView(this.getContext());
        textView.setText(this.getHeaderText(key, jsonNode, haveNext, true, haveChild));
        TextViewCompat.setTextAppearance(textView, style.JsonViewer_TextAppearance);
        textView.setFocusableInTouchMode(false);
        textView.setFocusable(false);
        textView.setTextIsSelectable(true);
        return textView;
    }

    private SpannableStringBuilder getHeaderText(Object key, @Nullable Object jsonNode, boolean haveNext, boolean childDisplayed, boolean haveChild) {
        SpannableStringBuilder b = new SpannableStringBuilder();
        if (key instanceof String) {
            b.append("\"", new ForegroundColorSpan(this.textColorKey), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            b.append((String) key, new ForegroundColorSpan(this.textColorKey), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            b.append("\"", new ForegroundColorSpan(this.textColorKey), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            b.append(": ");
        } else if (key != null) {
            b.append(key.toString());
            b.append(": ");
        }

        if (!childDisplayed) {
            if (jsonNode instanceof JSONArray) {
                b.append("[ ... ]");
            } else if (jsonNode instanceof JSONObject) {
                b.append("{ ... }");
            }
            if (haveNext) {
                b.append(",");
            }
        } else if (jsonNode instanceof JSONArray) {
            b.append("[");
            if (!haveChild) {
                b.append(this.getFooterText(jsonNode, haveNext));
            }
        } else if (jsonNode instanceof JSONObject) {
            b.append("{");
            if (!haveChild) {
                b.append(this.getFooterText(jsonNode, haveNext));
            }
        } else if (jsonNode != null) {
            if (jsonNode instanceof String) {
                b.append("\"" + jsonNode + "\"", new ForegroundColorSpan(this.textColorString), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            } else if (jsonNode instanceof Boolean) {
                b.append(jsonNode.toString(), new ForegroundColorSpan(this.textColorBool), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            } else if (jsonNode == JSONObject.NULL) {
                b.append(jsonNode.toString(), new ForegroundColorSpan(this.textColorNull), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            } else if (jsonNode instanceof Integer || jsonNode instanceof Float || jsonNode instanceof Double || jsonNode instanceof Long) {
                b.append(jsonNode.toString(), new ForegroundColorSpan(this.textColorNumber), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            } else {
                b.append(jsonNode.toString());
            }
            if (haveNext) {
                b.append(",");
            }
            LeadingMarginSpan span = new LeadingMarginSpan.Standard(0, (int) this.PADDING);
            b.setSpan(span, 0, b.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        return b;
    }

    private TextView getFooter(@Nullable Object jsonNode, boolean haveNext) {
        TextView textView = new TextView(this.getContext());
        textView.setText(this.getFooterText(jsonNode, haveNext));
        TextViewCompat.setTextAppearance(textView, style.JsonViewer_TextAppearance);
        textView.setFocusableInTouchMode(false);
        textView.setFocusable(false);
        return textView;
    }

    private StringBuilder getFooterText(@Nullable Object jsonNode, boolean haveNext) {
        StringBuilder builder = new StringBuilder();
        if (jsonNode instanceof JSONObject) {
            builder.append("}");
        } else if (jsonNode instanceof JSONArray) {
            builder.append("]");
        }
        if (haveNext) {
            builder.append(",");
        }
        return builder;
    }

    /**
     * 根据当前节点生成格式化（美化）的 JSON 字符串，缩进 4 格空格
     */
    private String getBeautifiedJson(Object jsonNode) {
        try {
            if (jsonNode instanceof JSONObject) {
                return ((JSONObject) jsonNode).toString(4);
            } else if (jsonNode instanceof JSONArray) {
                return ((JSONArray) jsonNode).toString(4);
            } else {
                return String.valueOf(jsonNode);
            }
        } catch (JSONException e) {
            Logger.e(e);
            return String.valueOf(jsonNode);
        }
    }

    public String getJSONString() {
        try {
            if (jsonObject instanceof JSONObject json) {
                return json.toString(4);
            }
            if (jsonObject instanceof JSONArray json) {
                return json.toString(4);
            }

        } catch (JSONException e) {
            Logger.e(e);
        }
        return "";
    }
}
