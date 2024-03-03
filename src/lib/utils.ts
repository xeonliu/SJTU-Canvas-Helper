export function formatDate(inputDate: string): string {
    if (!inputDate) {
        return "";
    }
    const date = new Date(inputDate);
    const year = date.getFullYear();
    const month = (date.getMonth() + 1).toString().padStart(2, '0');
    const day = date.getDate().toString().padStart(2, '0');
    const hours = date.getHours().toString().padStart(2, '0');
    const minutes = date.getMinutes().toString().padStart(2, '0');
    return `${year}/${month}/${day} ${hours}:${minutes}`;
}

export function sleep(time: number) {
    return new Promise((resolve) => setTimeout(resolve, time));
}

export function base64ToArrayBuffer(base64: string) {
    let binaryString = atob(base64);
    let length = binaryString.length;
    let bytes = new Uint8Array(length);

    for (let i = 0; i < length; i++) {
        bytes[i] = binaryString.charCodeAt(i);
    }

    return bytes.buffer;
}

export function base64ToFile(base64: string, filename: string) {
    let binaryString = atob(base64);
    let blob = new Blob([binaryString]);
    let file = new File([blob], filename);
    return file;
}

const fileExtensions: Record<string, string> = {
    bmp: "image/bmp",
    csv: "text/csv",
    doc: "doc",
    docx: "doc",
    gif: "image/gif",
    jpg: "image/jpg",
    jpeg: "image/jpeg",
    pptx: "ppt",
    pdf: "application/pdf",
    png: "image/png",
    tiff: "image/tiff",
    mp4: "video/mp4",
};

export function getFileType(filename: string): string {
    const extension = filename.split('.').pop()?.toLowerCase();
    if (extension && fileExtensions[extension]) {
        return fileExtensions[extension];
    } else {
        return extension ?? "";
    }
}