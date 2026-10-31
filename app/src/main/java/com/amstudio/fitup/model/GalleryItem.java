package com.amstudio.fitup.model;

import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;

public class GalleryItem implements Parcelable {
    private String id;
    private String uri;
    private String date;
    private String title;

    public GalleryItem(String uri, String date, String title) {
        this(String.valueOf(uri != null ? uri.hashCode() : System.currentTimeMillis()), uri, date, title);
    }

    public GalleryItem(String id, String uri, String date, String title) {
        this.id = id;
        this.uri = uri;
        this.date = date;
        this.title = title;
    }

    protected GalleryItem(Parcel in) {
        id = in.readString();
        uri = in.readString();
        date = in.readString();
        title = in.readString();
    }

    public static final Creator<GalleryItem> CREATOR = new Creator<GalleryItem>() {
        @Override
        public GalleryItem createFromParcel(Parcel in) {
            return new GalleryItem(in);
        }

        @Override
        public GalleryItem[] newArray(int size) {
            return new GalleryItem[size];
        }
    };

    public String getId() {
        return id;
    }

    public String getUri() {
        return uri;
    }

    public String getDate() {
        return date;
    }

    public String getTitle() {
        return title;
    }

    public void setUri(String uri) {
        this.uri = uri;
    }

    public void setDate(String date) {
        this.date = date;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        dest.writeString(id);
        dest.writeString(uri);
        dest.writeString(date);
        dest.writeString(title);
    }
}
