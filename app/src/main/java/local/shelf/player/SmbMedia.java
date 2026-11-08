package local.shelf.player;

import android.net.Uri;
import androidx.media3.common.C;
import androidx.media3.datasource.BaseDataSource;
import androidx.media3.datasource.DataSpec;
import com.hierynomus.msdtyp.AccessMask;
import com.hierynomus.msfscc.FileAttributes;
import com.hierynomus.mssmb2.SMB2CreateDisposition;
import com.hierynomus.mssmb2.SMB2CreateOptions;
import com.hierynomus.mssmb2.SMB2ShareAccess;
import com.hierynomus.smbj.SMBClient;
import com.hierynomus.smbj.SmbConfig;
import com.hierynomus.smbj.auth.AuthenticationContext;
import com.hierynomus.smbj.connection.Connection;
import com.hierynomus.smbj.share.DiskShare;
import com.hierynomus.smbj.share.File;
import com.hierynomus.smbj.share.Share;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.TimeUnit;

final class SmbMedia {
    private static final Set<String> VIDEO = Set.of("mkv", "mp4", "mov", "m4v", "webm", "ts", "m2ts", "avi");
    private static final Set<String> SUBTITLES = Set.of("srt", "ass", "ssa", "vtt");
    private static final SmbConfig CONFIG = SmbConfig.builder()
            .withTimeout(12, TimeUnit.SECONDS).withSoTimeout(20, TimeUnit.SECONDS).build();

    record Source(String host, int port, String share, String user, String domain, char[] password) {
        String identity() { return host + ":" + port + "/" + share; }
    }
    record Entry(String name, boolean directory) {}

    static List<Entry> list(Source source, String path) throws IOException {
        try (Remote remote = new Remote(source)) {
            return children(remote.share, path).stream().filter(entry -> entry.directory || isVideo(entry.name))
                    .sorted(Comparator.comparing(Entry::directory).reversed()
                            .thenComparing(Entry::name, String.CASE_INSENSITIVE_ORDER))
                    .collect(java.util.stream.Collectors.toList());
        }
    }

    static List<Catalog.Movie> index(Source source) throws IOException {
        try (Remote remote = new Remote(source)) {
            List<Catalog.Movie> movies = new ArrayList<>();
            scan(remote.share, "", movies, 0);
            return movies;
        }
    }

    private static List<Entry> children(DiskShare share, String path) {
        return share.list(path).stream().map(info -> new Entry(info.getFileName(),
                        (info.getFileAttributes() & FileAttributes.FILE_ATTRIBUTE_DIRECTORY.getValue()) != 0))
                .filter(entry -> !entry.name.startsWith("."))
                .collect(java.util.stream.Collectors.toList());
    }

    private static void scan(DiskShare share, String path, List<Catalog.Movie> movies, int depth) {
        if (depth > 32) return;
        List<Entry> entries = children(share, path);
        List<String> names = entries.stream().filter(entry -> !entry.directory).map(Entry::name)
                .collect(java.util.stream.Collectors.toList());
        for (Entry entry : entries) {
            String child = join(path, entry.name);
            if (entry.directory) {
                try {
                    scan(share, child, movies, depth + 1);
                } catch (com.hierynomus.mssmb2.SMBApiException error) {
                    if (error.getStatus() != com.hierynomus.mserref.NtStatus.STATUS_ACCESS_DENIED) throw error;
                }
            } else if (isVideo(entry.name)) {
                String stem = Catalog.basename(entry.name);
                String poster = artwork(names, path, stem, stem + "-poster", "poster", "folder");
                String backdrop = artwork(names, path, stem + "-fanart", stem + "-backdrop", "fanart", "backdrop");
                List<String> subs = names.stream().filter(name -> {
                    int dot = name.lastIndexOf('.');
                    if (dot < stem.length() || !SUBTITLES.contains(name.substring(dot + 1).toLowerCase(Locale.ROOT))) return false;
                    String base = name.substring(0, dot);
                    return base.equalsIgnoreCase(stem) || (base.length() > stem.length()
                            && base.regionMatches(true, 0, stem, 0, stem.length())
                            && ".-_".indexOf(base.charAt(stem.length())) >= 0);
                }).map(name -> join(path, name)).sorted().collect(java.util.stream.Collectors.toList());
                movies.add(new Catalog.Movie(child, Catalog.title(entry.name), poster, backdrop, subs));
            }
        }
    }

    private static String artwork(List<String> names, String path, String... candidates) {
        for (String candidate : candidates) {
            for (String name : names) {
                int dot = name.lastIndexOf('.');
                if (dot < 0 || !Catalog.basename(name).equalsIgnoreCase(candidate)) continue;
                String ext = name.substring(dot + 1).toLowerCase(Locale.ROOT);
                if (ext.equals("jpg") || ext.equals("jpeg") || ext.equals("png") || ext.equals("webp")) return join(path, name);
            }
        }
        return null;
    }

    private static String join(String path, String name) {
        return path.isEmpty() ? name : path + "\\" + name;
    }

    static byte[] readAsset(Source source, String path) throws IOException {
        try (Remote remote = new Remote(source);
             File file = openFile(remote.share, path)) {
            long size = file.getFileInformation().getStandardInformation().getEndOfFile();
            if (size <= 0 || size > 6 * 1024 * 1024) throw new IOException("Invalid artwork size");
            byte[] data = new byte[(int) size];
            int offset = 0;
            while (offset < data.length) {
                int count = file.read(data, offset, offset, Math.min(65536, data.length - offset));
                if (count <= 0) throw new IOException("Incomplete artwork");
                offset += count;
            }
            return data;
        }
    }

    static String hash(Source source, String path) throws IOException {
        try (Remote remote = new Remote(source); File file = openFile(remote.share, path)) {
            long size = file.getFileInformation().getStandardInformation().getEndOfFile();
            int block = 65536;
            if (size < 2L * block) return "";
            long result = size;
            byte[] bytes = new byte[block];
            for (long offset : new long[]{0, size - block}) {
                int total = 0;
                while (total < block) {
                    int count = file.read(bytes, offset + total, total, block - total);
                    if (count <= 0) throw new IOException("Incomplete SMB fingerprint");
                    total += count;
                }
                ByteBuffer buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
                while (buffer.hasRemaining()) result += buffer.getLong();
            }
            return String.format(Locale.ROOT, "%016x", result);
        }
    }

    private static File openFile(DiskShare share, String path) {
        return share.openFile(path, EnumSet.of(AccessMask.GENERIC_READ), null,
                SMB2ShareAccess.ALL, SMB2CreateDisposition.FILE_OPEN,
                EnumSet.of(SMB2CreateOptions.FILE_NON_DIRECTORY_FILE));
    }

    private static boolean isVideo(String name) {
        int dot = name.lastIndexOf('.');
        return dot >= 0 && VIDEO.contains(name.substring(dot + 1).toLowerCase(Locale.ROOT));
    }

    private static final class Remote implements AutoCloseable {
        private final SMBClient client = new SMBClient(CONFIG);
        private DiskShare share;

        Remote(Source source) throws IOException {
            try {
                Connection connection = client.connect(source.host, source.port);
                AuthenticationContext auth = source.user.isEmpty() ? AuthenticationContext.anonymous()
                        : new AuthenticationContext(source.user, source.password, source.domain);
                Share opened = connection.authenticate(auth).connectShare(source.share);
                if (!(opened instanceof DiskShare disk)) throw new IOException("Not a file share");
                share = disk;
            } catch (IOException | RuntimeException error) {
                close();
                throw error;
            }
        }

        @Override public void close() {
            try {
                if (share != null) share.close();
            } catch (Exception ignored) {
            } finally {
                try {
                    client.close();
                } catch (Exception ignored) {
                }
            }
        }
    }

    static final class Input extends BaseDataSource {
        private final Source source;
        private Remote remote;
        private File file;
        private Uri uri;
        private long offset;
        private long remaining;

        Input(Source source) {
            super(true);
            this.source = source;
        }

        @Override public long open(DataSpec spec) throws IOException {
            transferInitializing(spec);
            try {
                remote = new Remote(source);
                file = openFile(remote.share, String.join("\\", spec.uri.getPathSegments()));
                long size = file.getFileInformation().getStandardInformation().getEndOfFile();
                offset = spec.position;
                if (offset > size) throw new IOException("Read position beyond end of file");
                remaining = spec.length == C.LENGTH_UNSET ? size - offset : Math.min(size - offset, spec.length);
                uri = spec.uri;
                transferStarted(spec);
                return remaining;
            } catch (IOException | RuntimeException error) {
                close();
                throw new IOException("SMB open failed", error);
            }
        }

        @Override public int read(byte[] data, int start, int length) throws IOException {
            if (length == 0) return 0;
            if (remaining == 0) return C.RESULT_END_OF_INPUT;
            try {
                int count = file.read(data, offset, start, (int) Math.min(remaining, length));
                if (count <= 0) return C.RESULT_END_OF_INPUT;
                offset += count;
                remaining -= count;
                bytesTransferred(count);
                return count;
            } catch (RuntimeException error) {
                throw new IOException("SMB read failed", error);
            }
        }

        @Override public Uri getUri() { return uri; }

        @Override public void close() {
            try {
                if (file != null) file.close();
            } catch (Exception ignored) {
            } finally {
                file = null;
                if (remote != null) remote.close();
                remote = null;
                if (uri != null) {
                    uri = null;
                    transferEnded();
                }
            }
        }
    }
}
