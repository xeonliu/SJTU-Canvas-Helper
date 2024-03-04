import CodeRenderer from "./code_renderer";
import DocxRenderer from "./docx_renderer";
import ImageRenderer from "./img_renderer";
import MarkdownRenderer from "./markdown_renderer";
import { BMPRenderer, CSVRenderer, GIFRenderer, JPGRenderer, PDFRenderer, PNGRenderer, TIFFRenderer, VideoRenderer, MSDocRenderer } from "@cyntler/react-doc-viewer";

export const BasicRenderers = [BMPRenderer, JPGRenderer, PDFRenderer, PNGRenderer, TIFFRenderer, CSVRenderer, GIFRenderer, VideoRenderer, MSDocRenderer,
    CodeRenderer, DocxRenderer, ImageRenderer, MarkdownRenderer
]