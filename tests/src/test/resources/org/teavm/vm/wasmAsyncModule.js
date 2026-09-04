export function decodedLengthAsync(data, resolve, reject) {
    Promise.resolve({ length: data.length }).then(resolve, reject);
}
