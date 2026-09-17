package com.konasl.nagad

/**
 * অ্যাপ এখন ফোরগ্রাউন্ডে (কোনো একটিভিটি রিজিউমড অবস্থায় দৃশ্যমান) আছে কিনা
 * তার সাধারণ গ্লোবাল স্টেট।
 *
 * DoctorAlertService এটা চেক করে ঠিক করে:
 *  - অ্যাপ ফোরগ্রাউন্ডে থাকলে → সিস্টেম heads-up নোটিফিকেশন না দেখিয়ে
 *    শুধু একটা লোকাল ব্রডকাস্ট পাঠানো হয়, যা MyApp ধরে কাস্টম ডায়ালগ
 *    (DoctorAlertDialogHelper) দেখায়।
 *  - অ্যাপ ব্যাকগ্রাউন্ডে থাকলে → ডিফল্ট সাউন্ডসহ সিস্টেম নোটিফিকেশন দেখানো হয়।
 */
object AppForegroundState {
    @Volatile
    var isAppInForeground: Boolean = false
}
