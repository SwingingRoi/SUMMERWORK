package com.example.myapplication.Activity.Home;

import android.annotation.SuppressLint;
import android.app.Fragment;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Handler;
import android.os.Message;
import android.util.DisplayMetrics;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.example.myapplication.Activity.Book.BookActivity;

import com.example.myapplication.InternetUtils.GetServer;
import com.example.myapplication.InternetUtils.HttpUtils;
import com.example.myapplication.MyComponent.MyToast;
import com.example.myapplication.PicUtils.GetPicture;
import com.example.myapplication.R;

import org.json.JSONArray;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class HomeFragment extends Fragment {


    final private int NEWBOOKS = 1;
    final private int SURFACE = 2;

    private LinearLayout normal;
    private LinearLayout loadView;
    private LinearLayout bookTable;
    private ImageView Refresh;
    private ScrollView scrollView;
    private View pullDown;//请求文字提示

    private boolean isRequesting = false;//当前是否在向后端请求书本信息
    private int from=0;//当前获取的works从第几本书开始
    private boolean firstIn = true;//是否是第一次进入该页面
    final private int PAGESIZE=10;
    private JSONArray books = new JSONArray();
    private List<Drawable> tag_border_styles;//标签边框样式

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.activity_home, container, false);

        final ImageView searchIcon = view.findViewById(R.id.SearchIcon);

        searchIcon.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(getActivity(),SearchActivity.class);
                startActivity(intent);
            }
        });

        tag_border_styles = new ArrayList<>();
        tag_border_styles.add(getResources().getDrawable(R.drawable.book_tag_border_red));
        tag_border_styles.add(getResources().getDrawable(R.drawable.book_tag_border_brown));
        tag_border_styles.add(getResources().getDrawable(R.drawable.book_tag_border_blue));

        normal = view.findViewById(R.id.normal);
        bookTable = view.findViewById(R.id.BookTable);

        Refresh = view.findViewById(R.id.refresh);
        Refresh.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                refresh();
            }
        });
        pullDown = LayoutInflater.from(getActivity()).inflate(R.layout.pull_down, null);
        bookTable.addView(pullDown);

        scrollView = view.findViewById(R.id.scroll);
        scrollView.setOnTouchListener(new View.OnTouchListener() {

            @Override
            public boolean onTouch(View v, MotionEvent event) {

                if(event.getAction() == MotionEvent.ACTION_UP) {
                    if (!isRequesting && scrollView.getChildAt(0).getMeasuredHeight() <= scrollView.getScrollY() + scrollView.getHeight()) {
                        isRequesting = true;
                        new Thread(getBooks).start();//向后端请求更多书本
                    }
                }
                return false;
            }
        });

        DisplayMetrics displayMetrics = getResources().getDisplayMetrics();
        int height = displayMetrics.heightPixels;
        LinearLayout.LayoutParams params = (LinearLayout.LayoutParams)scrollView.getLayoutParams();
        params.height = (int) (height/1.28);
        scrollView.setLayoutParams(params);

        loadView = view.findViewById(R.id.Loading);
        loadView.setVisibility(View.VISIBLE);
        loadView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                refresh();
            }
        });
        normal.setVisibility(View.INVISIBLE);
        refresh();
        return view;
    }

    public void jumpToBook(int bookid){
        Intent intent = new Intent(getActivity(),BookActivity.class);
        intent.putExtra("id",bookid);
        startActivity(intent);
    }

    public void refresh(){

        Refresh.setClickable(false);
        loadView.setClickable(false);

        CountDownTimer countDownTimer = new CountDownTimer(5000,1000) {
            @Override
            public void onTick(long millisUntilFinished) {

            }

            @Override
            public void onFinish() {
                loadView.setClickable(true);
                Refresh.setClickable(true);
            }
        };//防止用户高频率点击
        countDownTimer.start();

        if (isRequesting) return;

        isRequesting = true;

        if (!firstIn) {
            from = 0;
            firstIn = true;
            bookTable.removeAllViews();
            books = new JSONArray();
        }

        loadView.setVisibility(View.VISIBLE);//加载画面
        loadView.findViewById(R.id.loadinggif).setVisibility(View.VISIBLE);
        loadView.findViewById(R.id.Remind).setVisibility(View.INVISIBLE);
        normal.setVisibility(View.INVISIBLE);

        new Thread(getBooks).start();
    }

    Runnable getBooks = new Runnable() {
        @Override
        public void run() {
            try{
            System.out.println("get books begin");

            getActivity().runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    TextView text = pullDown.findViewById(R.id.content);
                    text.setText(getResources().getString(R.string.isReq));
                }
            });



            final GetServer getServer = new GetServer();
            String url = getServer.getIPADDRESS()+"/audiobook/getbooks?from=" + from + "&size=" + PAGESIZE;

            System.out.println("begin request");
            HttpUtils httpUtils = new HttpUtils(url);
            ByteArrayOutputStream outputStream = httpUtils.doHttp(null, "GET",
                    "application/json");


            if (outputStream == null) {//请求超时
                getActivity().runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        new MyToast(HomeFragment.this.getActivity(), getResources().getString(R.string.HttpTimeOut));
                        isRequesting = false;

                        loadView.findViewById(R.id.loadinggif).setVisibility(View.INVISIBLE);
                        if (firstIn) {//如果是首次进入，设置点击屏幕刷新提醒
                            loadView.findViewById(R.id.Remind).setVisibility(View.VISIBLE);
                        }
                    }
                });
                return;
            }

            final String result = new String(outputStream.toByteArray(),
                    StandardCharsets.UTF_8);

            System.out.println("request done");

            final JSONArray newBooks = new JSONArray(result);


            for (int i = 0; i < newBooks.length(); i++) {
                books.put(newBooks.getJSONObject(i));
            }//向works中添加新请求过来的work

            getActivity().runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    try {
                        bookTable.removeView(pullDown);

                        if (firstIn) {//如果是首次进入该activity
                            firstIn = false;

                            if (books.length() == 0) {//说明数据库中没有书本
                                View nobookView = LayoutInflater.from(HomeFragment.this.getActivity()).inflate(R.layout.no_book_style, null);
                                bookTable.addView(nobookView);
                                isRequesting = false;
                                return;
                            }
                        }

                        for (int i = 0; i < newBooks.length(); i++) {
                            try {
                                View bookRow = LayoutInflater.from(HomeFragment.this.getActivity()).inflate(R.layout.book_row_style, null);

                                TextView title = bookRow.findViewById(R.id.BookName);
                                title.setText(newBooks.getJSONObject(i).getString("name"));

                                TextView viewNumber = bookRow.findViewById(R.id.viewnumber);
                                viewNumber.setText(String.valueOf(newBooks.getJSONObject(i).getInt("views")));

                                TextView chapterNumber = bookRow.findViewById(R.id.chapternumber);
                                chapterNumber.setText(newBooks.getJSONObject(i).getInt("chapters") + "章");

                                LinearLayout tagsView = bookRow.findViewById(R.id.tags);
                                String tagStr = newBooks.getJSONObject(i).getString("tags");
                                String[] tags = tagStr.split(" ");

                                for(int j=0;j<tags.length;j++){
                                    String tag = tags[j];
                                    View tagView = LayoutInflater.from(getActivity()).inflate(R.layout.book_tag,null);
                                    TextView t = tagView.findViewById(R.id.tag);
                                    t.setText(tag);
                                    t.setBackground(tag_border_styles.get(j));

                                    LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT,
                                            LinearLayout.LayoutParams.WRAP_CONTENT);
                                    layoutParams.setMargins(15,0,0,0);
                                    tagView.setLayoutParams(layoutParams);

                                    tagsView.addView(tagView);
                                }



                                bookTable.addView(bookRow);

                                final int id = newBooks.getJSONObject(i).getInt("id");
                                bookRow.setOnClickListener(new View.OnClickListener() {
                                    @Override
                                    public void onClick(View v) {
                                        jumpToBook(id);
                                    }
                                });
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        }

                        bookTable.addView(pullDown);

                        if(newBooks.length() < PAGESIZE){
                            TextView textView = pullDown.findViewById(R.id.content);
                            textView.setText(getResources().getString(R.string.hasEnd));
                            isRequesting = false;
                        }//说明书本已请求完毕
                        else {
                            TextView textView = pullDown.findViewById(R.id.content);
                            textView.setText(getResources().getString(R.string.pullDown));
                            isRequesting = false;
                        }

                        for (int i = from; i < books.length(); i++) {
                            Thread thread = new GetSurface(i);
                            thread.start();
                        }

                        from = from + newBooks.length();//更新请求index

                        loadView.setVisibility(View.INVISIBLE);
                        normal.setVisibility(View.VISIBLE);

                    }catch (Exception e){
                        e.printStackTrace();
                    }
                }
            });

            }catch (Exception e){
                e.printStackTrace();
            }
        }
    };

    private class GetSurface extends Thread
    {
        private int index;

        public GetSurface(int index){
            this.index = index;
        }

        @Override
        public void run() {
            try {
                GetPicture getPicture = new GetPicture();
                final Bitmap surface = getPicture.getSurface(books.getJSONObject(index).getInt("id"));

                getActivity().runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (surface != null) {
                            View bookRow = bookTable.getChildAt(index);
                            ImageView surfaceView = bookRow.findViewById(R.id.surface);
                            surfaceView.setImageBitmap(surface);
                        }
                    }
                });
            }catch (Exception e){
                e.printStackTrace();
            }
        }
    }
}
