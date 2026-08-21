class SubscriptionModel {
  SubscriptionModel({required this.id, required this.userId, required this.planType, required this.status, this.transactionId, this.startDate, this.endDate, this.createdAt});
  factory SubscriptionModel.fromJson(Map<String, dynamic> j) => SubscriptionModel(
        id: j['id'] as String,
        userId: j['user_id'] as String,
        planType: j['plan_type'] as String,
        status: j['status'] as String,
        transactionId: j['transaction_id'] as String?,
        startDate: j['start_date'] != null ? DateTime.tryParse(j['start_date'] as String) : null,
        endDate: j['end_date'] != null ? DateTime.tryParse(j['end_date'] as String) : null,
        createdAt: j['created_at'] != null ? DateTime.tryParse(j['created_at'] as String) : null,
      );
  final String id;
  final String userId;
  final String planType;
  final String status;
  final String? transactionId;
  final DateTime? startDate;
  final DateTime? endDate;
  final DateTime? createdAt;

  bool get isActive => status == 'active';
  bool get isPending => status == 'pending';
}
