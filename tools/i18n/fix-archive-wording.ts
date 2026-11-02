/**
 * One-off fix (1.13.2): in 18 locales the archive flow was translated as "delete"
 * ("Delete email?", "This action cannot be undone"), although archiving is
 * reversible and the list even offers Undo. Rewrites the seven archive strings
 * per locale, using each locale's own word from `list_archived_snackbar`.
 *
 *   bun tools/i18n/fix-archive-wording.ts
 */
import { readFileSync, writeFileSync } from "node:fs";

const RES = new URL("../../app/src/main/res/", import.meta.url).pathname;
type Row = [filter: string, state: string, button: string, confirm: string, done: string, title: string, message: string];

const T: Record<string, Row> = {
  de: ["Archiviert", "Archiviert", "Maske archivieren", "Archivieren", "Archiviert", "Diese Maske archivieren?", "E-Mails an diese Adresse werden zurückgewiesen. Sie können die Maske später wiederherstellen."],
  es: ["Archivados", "Archivado", "Archivar máscara", "Archivar", "Archivada", "¿Archivar esta máscara?", "El correo enviado a esta dirección será rechazado. Puedes restaurarla más tarde."],
  fr: ["Archivés", "Archivé", "Archiver le masque", "Archiver", "Archivé", "Archiver ce masque ?", "Les e-mails envoyés à cette adresse seront rejetés. Vous pourrez le restaurer plus tard."],
  it: ["Archiviate", "Archiviata", "Archivia maschera", "Archivia", "Archiviata", "Archiviare questa maschera?", "La posta inviata a questo indirizzo verrà respinta. Potrai ripristinarla in seguito."],
  pt: ["Arquivados", "Arquivado", "Arquivar máscara", "Arquivar", "Arquivada", "Arquivar esta máscara?", "Os e-mails enviados para este endereço serão devolvidos. Você pode restaurá-la depois."],
  nl: ["Gearchiveerd", "Gearchiveerd", "Masker archiveren", "Archiveren", "Gearchiveerd", "Dit masker archiveren?", "E-mail naar dit adres wordt geweigerd. U kunt het masker later herstellen."],
  ru: ["Архивные", "Архивирована", "Архивировать маску", "Архивировать", "Архивировано", "Архивировать эту маску?", "Письма на этот адрес будут отклоняться. Маску можно восстановить позже."],
  uk: ["Архівні", "Архівована", "Архівувати маску", "Архівувати", "Архівовано", "Архівувати цю маску?", "Листи на цю адресу відхилятимуться. Маску можна відновити пізніше."],
  tr: ["Arşivlenmiş", "Arşivlenmiş", "Maskeyi arşivle", "Arşivle", "Arşivlendi", "Bu maske arşivlensin mi?", "Bu adrese gönderilen e-postalar geri döner. Maskeyi daha sonra geri yükleyebilirsiniz."],
  ar: ["مؤرشف", "مؤرشف", "أرشفة القناع", "أرشفة", "تمت الأرشفة", "أرشفة هذا القناع؟", "سيتم رفض الرسائل المرسلة إلى هذا العنوان. يمكنك استعادة القناع لاحقًا."],
  hi: ["संग्रहीत", "संग्रहीत", "मास्क संग्रहीत करें", "संग्रहीत करें", "संग्रहीत किया गया", "इस मास्क को संग्रहीत करें?", "इस पते पर भेजे गए ईमेल वापस लौट जाएंगे। आप मास्क को बाद में पुनर्स्थापित कर सकते हैं।"],
  bn: ["আর্কাইভ করা", "আর্কাইভ করা", "মাস্ক আর্কাইভ করুন", "আর্কাইভ করুন", "আর্কাইভ করা হয়েছে", "এই মাস্কটি আর্কাইভ করবেন?", "এই ঠিকানায় পাঠানো মেইল ফেরত যাবে। আপনি পরে মাস্কটি পুনরুদ্ধার করতে পারবেন।"],
  id: ["Diarsipkan", "Diarsipkan", "Arsipkan topeng", "Arsipkan", "Diarsipkan", "Arsipkan topeng ini?", "Email yang dikirim ke alamat ini akan ditolak. Anda dapat memulihkan topeng ini nanti."],
  ja: ["アーカイブ済み", "アーカイブ済み", "マスクをアーカイブ", "アーカイブ", "アーカイブしました", "このマスクをアーカイブしますか？", "このアドレス宛てのメールは返送されます。マスクは後で復元できます。"],
  ko: ["보관됨", "보관됨", "마스크 보관", "보관", "보관됨", "이 마스크를 보관할까요?", "이 주소로 보낸 메일은 반송됩니다. 마스크는 나중에 복원할 수 있습니다."],
  th: ["เก็บถาวรแล้ว", "เก็บถาวรแล้ว", "เก็บถาวรหน้ากาก", "เก็บถาวร", "เก็บถาวรแล้ว", "เก็บถาวรหน้ากากนี้หรือไม่", "อีเมลที่ส่งมายังที่อยู่นี้จะถูกตีกลับ คุณสามารถกู้คืนหน้ากากได้ในภายหลัง"],
  vi: ["Đã lưu trữ", "Đã lưu trữ", "Lưu trữ mặt nạ", "Lưu trữ", "Đã lưu trữ", "Lưu trữ mặt nạ này?", "Thư gửi tới địa chỉ này sẽ bị trả lại. Bạn có thể khôi phục mặt nạ sau."],
  "b+zh+Hans": ["已存档", "已存档", "存档面具", "存档", "已存档", "存档这个面具？", "发往此地址的邮件将被退回。您可以稍后恢复此面具。"],
};
const KEYS = [
  "filter_deleted",
  "state_deleted",
  "email_detail_delete",
  "email_detail_delete_confirm",
  "email_detail_deleted",
  "email_detail_delete_dialog_title",
  "email_detail_delete_dialog_message",
] as const;

const xml = (s: string) => s.replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/'/g, "\\'").replace(/"/g, '\\"');

for (const [locale, row] of Object.entries(T)) {
  const file = `${RES}values-${locale}/strings.xml`;
  let text = readFileSync(file, "utf8");
  KEYS.forEach((key, i) => {
    const re = new RegExp(`(<string name="${key}"[^>]*>)([\\s\\S]*?)(</string>)`);
    if (!re.test(text)) throw new Error(`${locale}: ${key} not found`);
    text = text.replace(re, (_m, open, _old, close) => open + xml(row[i]) + close);
  });
  writeFileSync(file, text);
  console.log(`values-${locale}: ${KEYS.length} strings`);
}
