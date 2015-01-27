package htsjdk.samtools.cram.structure;

import htsjdk.samtools.SAMBinaryTagAndValue;
import htsjdk.samtools.SAMFileHeader;
import htsjdk.samtools.SAMTagUtil;
import htsjdk.samtools.SAMTextHeaderCodec;
import htsjdk.samtools.cram.encoding.Encoding;
import htsjdk.samtools.cram.io.ByteBufferUtils;

import javax.swing.*;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.lang.reflect.Array;
import java.util.Arrays;
import java.util.HashMap;

/**
 * Created by vadim on 22/01/2015.
 */
public class CramInspector extends JFrame {

    JComponent viewerContainer;
    HashMap<Class, JComponent> viewers = new HashMap<Class, JComponent>();
    JComponent nullViewer = new JLabel("Nothing to see here.");

    public CramInspector(String name, InputStream is) throws HeadlessException, IOException, IllegalAccessException {
        setTitle(name == null ? "Unknown" : name);
        setSize(800, 600);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(EXIT_ON_CLOSE);

        initViewers();

        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);

        CramJTree tree = new CramJTree(name, is);
        JScrollPane treeScrollPane = new JScrollPane(tree);
        splitPane.add(treeScrollPane, JSplitPane.LEFT);

        viewerContainer = new JPanel(new BorderLayout());
        viewerContainer.setBackground(Color.green);
        splitPane.add(viewerContainer, JSplitPane.RIGHT);
        splitPane.setDividerLocation(0.25);
        splitPane.setBackground(Color.cyan);

        add(splitPane);

        tree.addTreeSelectionListener(new TreeSelectionListener() {
            @Override
            public void valueChanged(final TreeSelectionEvent e) {
                final TreePath path = e.getPath();
                final Object lastPathComponent = path.getLastPathComponent();
                if (lastPathComponent != null && lastPathComponent instanceof DefaultMutableTreeNode) {
                    DefaultMutableTreeNode node = (DefaultMutableTreeNode) lastPathComponent;
                    Object object = node.getUserObject();
                    CramInspector.this.show(object);
                }
            }
        });

        setBackground(Color.red);
    }

    private void initViewers() {
        viewers.put(CramHeader.class, new CramHeaderViewer());
        viewers.put(Container.class, new CramContainerViewer());
        viewers.put(Slice.class, new CramSliceViewer());
        viewers.put(Block.class, new CramBlockViewer());
        viewers.put(CompressionHeader.class, new CramCompressionHeaderViewer());
        viewers.put(Encoding.class, new EncodingViewer());
        viewers.put(EncodingKey.class, new EncodingKeyViewer());
    }

    private JComponent getViewerFor(Object object) {
        if (object == null) return nullViewer;

        JComponent viewer = viewers.get(object.getClass());
        if (viewer == null) {
            for (Class c:viewers.keySet()) {
                if (c.isInstance(object)) return viewers.get(c) ;
            }
            return nullViewer;
        }
        return viewer;
    }

    private void show(Object object) {
        JComponent viewer = getViewerFor(object);
        if (viewer instanceof CramObjectViewer) ((CramObjectViewer) viewer).setObjectToView(object);

        viewerContainer.removeAll();
        viewerContainer.add(viewer, BorderLayout.CENTER);
        viewerContainer.validate();
        viewerContainer.repaint();
    }

    public static void main(String[] args) throws FileNotFoundException {
        EventQueue.invokeLater(new Runnable() {
            @Override
            public void run() {
                try {
                    final CramInspector inspector = new CramInspector("test", new FileInputStream("C:\\temp\\CRAM3\\v30.cram"));
                    inspector.setVisible(true);
                } catch (FileNotFoundException e) {
                    throw new RuntimeException(e);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                } catch (IllegalAccessException e) {
                    throw new RuntimeException(e);
                }
            }
        });
    }

    private static abstract class CramObjectViewer extends JPanel {
        public abstract void setObjectToView(Object object);
    }

    private static class CramHeaderViewer extends CramTextViewer {

        @Override
        protected String getText(final Object object) {
            CramHeader cramHeader = (CramHeader) object;
            StringBuilder sb = new StringBuilder();
            sb.append("Version: ").append(cramHeader.getMajorVersion()).append(".").append(cramHeader.getMinorVersion()).append("\n");
            sb.append("ID: ").append(ByteBufferUtils.toHexString(cramHeader.getId()));
            final SAMFileHeader samFileHeader = cramHeader.getSamFileHeader();
            SAMTextHeaderCodec codec = new SAMTextHeaderCodec();
            StringWriter stringWriter = new StringWriter();
            codec.encode(stringWriter, samFileHeader);
            sb.append(stringWriter.toString());

            return sb.toString();
        }
    }

    private static class CramContainerViewer extends CramTextViewer {
        @Override
        protected String getText(final Object object) {
            Container container = (Container) object;

            StringBuilder sb = new StringBuilder();
            sb.append(String.format("Reference: %d:%d-%d\n", container.sequenceId, container.alignmentStart, container.alignmentSpan));
            sb.append(String.format("Blocks: %d\n", container.blockCount));
            sb.append(String.format("Offset: %d\n", container.offset));
            sb.append(String.format("Bases: %d\n", container.bases));
            sb.append(String.format("Checksum: 0x%s\n", ByteBufferUtils.toHexString(ByteBufferUtils.writeInt32(container.checksum))));
            sb.append(String.format("Size in bytes: %d\n", container.containerByteSize));
            sb.append(String.format("GRC: %d\n", container.globalRecordCounter));
            sb.append(String.format("EOF: %s\n", container.isEOF()));
            sb.append(String.format("Landmarks: %s\n", Arrays.toString(container.landmarks)));
            sb.append(String.format("Slices: %d\n", container.slices == null ? 0 : container.slices.length));
            return sb.toString();
        }
    }

    private static class CramBlockViewer extends CramTextViewer {
        @Override
        protected String getText(final Object object) {
            Block block = (Block) object;

            StringBuilder sb = new StringBuilder();
            sb.append(String.format("Content ID: %d\n", block.contentId));
            sb.append(String.format("Content type: %s\n", block.contentType.name()));
            sb.append(String.format("Raw size: %d\n", block.getRawContentSize()));
            sb.append(String.format("Compressed: %s\n", block.isCompressed()));
            sb.append(String.format("Uncompressed: %s\n", block.isUncompressed()));
            sb.append(String.format("Method: %s\n", block.method.name()));

            byte[] data = block.getRawContent() ;
            int len = Math.min(30, data.length) ;
            byte[] preview = Arrays.copyOfRange(data, 0, len) ;
            sb.append("ASCII: ").append(new String (preview)).append("\n") ;
            sb.append("Bytes: ").append(Arrays.toString(preview)) ;

            return sb.toString();
        }
    }

    private static class CramSliceViewer extends CramTextViewer {
        @Override
        protected String getText(final Object object) {
            Slice slice = (Slice) object;

            StringBuilder sb = new StringBuilder();
            sb.append(String.format("Content ID: %d\n", slice.sequenceId));
            sb.append(String.format("Alignment start: %d\n", slice.alignmentStart));
            sb.append(String.format("Alignment span: %d\n", slice.alignmentSpan));
            sb.append(String.format("Bases: %d\n", slice.bases));
            sb.append(String.format("Container offset: %d\n", slice.containerOffset));
            sb.append(String.format("Content IDs: %s\n", Arrays.toString(slice.contentIDs)));
            sb.append(String.format("Content type: %s\n", slice.contentType == null ? "null" : slice.contentType.name()));
            sb.append(String.format("Embedded ref block ID: %d\n", slice.embeddedRefBlockContentID));
            sb.append(String.format("External blocks: %d\n", slice.external.size()));
            sb.append(String.format("GRC: %d\n", slice.globalRecordCounter));
            sb.append(String.format("Index: %d\n", slice.index));
            sb.append(String.format("Blocks: %d\n", slice.nofBlocks));
            sb.append(String.format("Records: %d\n", slice.nofRecords));
            sb.append(String.format("Offset: %d\n", slice.offset));
            sb.append(String.format("Ref MD5: %s\n", ByteBufferUtils.toHexString(slice.refMD5)));
            sb.append(String.format("Size: %d\n", slice.size));
            if (slice.sliceTags != null) {
                SAMBinaryTagAndValue tags = slice.sliceTags;
                while (tags != null) {
                    String tagID = SAMTagUtil.getSingleton().makeStringTag(tags.tag);
                    String value = null;
                    if (tags.value instanceof Array) value = Arrays.toString((Object[]) tags.value);
                    else value = tags.value.toString();
                    sb.append(String.format("Tag: %s\n", tags.tag, tags.value));
                    tags = tags.getNext();
                }
            }
            return sb.toString();
        }
    }

    private static class CramCompressionHeaderViewer extends CramTextViewer {
        @Override
        protected String getText(final Object object) {
            CompressionHeader h = (CompressionHeader) object;

            StringBuilder sb = new StringBuilder();
            sb.append(String.format("AP delta: %s\n", h.AP_seriesDelta));
            sb.append(String.format("Read names included: %s\n", h.readNamesIncluded));
            sb.append(String.format("Ref required: %s\n", h.referenceRequired));

            sb.append(String.format("Subs matrix: %s\n", h.substitutionMatrix.toString()));

            sb.append(String.format("External compressors:\n"));
            for (Integer id : h.externalCompressors.keySet())
                sb.append(String.format("\t%d\t%s: %d\n", id, h.externalCompressors.get(id)));


            if (h.externalIds != null)
                sb.append(String.format("External IDs: %d\n", Arrays.toString(h.externalIds.toArray())));

            sb.append(String.format("Dictionary:\n"));
            for (int id = 0; id < h.dictionary.length; id++) {
                byte[][] tagIds = h.dictionary[id];
                StringBuilder s2 = new StringBuilder();
                for (int t = 0; t < tagIds.length; t++)
                    s2.append("\t").append(new String(tagIds[t]));
                sb.append(s2.toString()).append("\n");
            }

            sb.append(String.format("Encodings:\n"));
            for (EncodingKey key : h.eMap.keySet())
                sb.append(String.format("\t%s\t%s\n", key.name(), h.eMap.get(key)));


            sb.append(String.format("Tags:\n"));
            for (Integer tc : h.tMap.keySet())
                sb.append(String.format("\t%s\t%s\n", SAMTagUtil.getSingleton().makeStringTag(tc.shortValue()), h.tMap.get(tc)));
            return sb.toString();
        }
    }

    private static class EncodingKeyViewer extends CramTextViewer {
        @Override
        protected String getText(final Object object) {
            EncodingKey encodingKey = (EncodingKey) object;

            StringBuilder sb = new StringBuilder();
            sb.append(String.format("Encoding key: %s\n", encodingKey.name()));
            return sb.toString();
        }
    }

    private static class EncodingViewer extends CramTextViewer {
        @Override
        protected String getText(final Object object) {
            Encoding encoding = (Encoding) object;

            StringBuilder sb = new StringBuilder();
            sb.append(String.format("Encoding: id=%s; params: %s\n", encoding.id(), Arrays.toString(encoding.toByteArray())));
            return sb.toString();
        }
    }

    private static abstract class CramTextViewer extends CramObjectViewer {
        private JTextArea label = new JTextArea("nothing here");
        private JScrollPane scrollPane;

        public CramTextViewer() {
            setLayout(new BorderLayout());
            scrollPane = new JScrollPane(label);
            add(scrollPane, BorderLayout.CENTER);
        }

        protected abstract String getText(Object object);

        @Override
        public void setObjectToView(final Object object) {
            label.setText(getText(object));
            SwingUtilities.invokeLater(new Runnable() {
                @Override
                public void run() {
                    scrollPane.getVerticalScrollBar().setValue(0);
                    scrollPane.getHorizontalScrollBar().setValue(0);
                }
            });
        }
    }
}
