package android.provider;

import android.net.Uri;

/** MediaStore constants + URIs. */
public class MediaStore {

    public static final String ACTION_MEDIA_SCANNER_SCAN_FILE =
            "android.intent.action.MEDIA_SCANNER_SCAN_FILE";
    public static final String VOLUME_EXTERNAL = "external";
    public static final String VOLUME_EXTERNAL_PRIMARY = "external_primary";

    public static class MediaColumns {
        public MediaColumns() {}
        public static final String DISPLAY_NAME = "_display_name";
        public static final String MIME_TYPE = "mime_type";
        public static final String RELATIVE_PATH = "relative_path";
        public static final String IS_PENDING = "is_pending";
        public static final String DATA = "_data";
        public static final String SIZE = "_size";
        public static final String DATE_ADDED = "date_added";
        public static final String DATE_MODIFIED = "date_modified";
        public static final String WIDTH = "width";
        public static final String HEIGHT = "height";
        public static final String DURATION = "duration";
        public static final String _ID = "_id";
    }

    public static class MediaColumnsExtras {
        public static final String IS_TRASHED = "is_trashed";
    }

    public interface ImagesColumns {
        String TITLE = "title";
        String DESCRIPTION = "description";
    }

    public static class Images {
        public static final Uri EXTERNAL_CONTENT_URI = Uri.parse(
                "content://media/external/images/media");
        public static final Uri INTERNAL_CONTENT_URI = Uri.parse(
                "content://media/internal/images/media");
        public static final String CONTENT_TYPE = "vnd.android.cursor.dir/image";

        public static class Media extends MediaColumns {
            public static final Uri EXTERNAL_CONTENT_URI = Images.EXTERNAL_CONTENT_URI;
            public static final Uri INTERNAL_CONTENT_URI = Images.INTERNAL_CONTENT_URI;
        }
    }

    public static class Video {
        public static final Uri EXTERNAL_CONTENT_URI = Uri.parse(
                "content://media/external/video/media");
        public static final Uri INTERNAL_CONTENT_URI = Uri.parse(
                "content://media/internal/video/media");
        public static final String CONTENT_TYPE = "vnd.android.cursor.dir/video";

        public static class Media extends MediaColumns {
            public static final Uri EXTERNAL_CONTENT_URI = Video.EXTERNAL_CONTENT_URI;
            public static final Uri INTERNAL_CONTENT_URI = Video.INTERNAL_CONTENT_URI;
        }
    }

    public static class Audio {
        public static final Uri EXTERNAL_CONTENT_URI = Uri.parse(
                "content://media/external/audio/media");

        public static class Media extends MediaColumns {
            public static final Uri EXTERNAL_CONTENT_URI = Audio.EXTERNAL_CONTENT_URI;
        }
    }

    public static class Files {
        public static final Uri EXTERNAL_CONTENT_URI = Uri.parse(
                "content://media/external/file");
        public static final String CONTENT_TYPE = "vnd.android.cursor.dir/files";
    }

    public static class MediaColumnsAlias extends MediaColumns { }
}
