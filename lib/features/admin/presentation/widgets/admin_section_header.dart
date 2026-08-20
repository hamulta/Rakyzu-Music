import 'package:flutter/cupertino.dart';
import 'package:flutter/material.dart';

import '../../../../core/theme/app_colors.dart';

class AdminSectionHeader extends StatelessWidget {
  const AdminSectionHeader({
    super.key,
    required this.title,
    required this.count,
    this.onViewAll,
    this.onAdd,
  });

  final String title;
  final int count;
  final VoidCallback? onViewAll;
  final VoidCallback? onAdd;

  @override
  Widget build(BuildContext context) {
    return Row(
      children: [
        Text(title, style: Theme.of(context).textTheme.titleLarge),
        const SizedBox(width: 8),
        Container(
          padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 2),
          decoration: BoxDecoration(
            color: AppColors.azureMistDeep.withOpacity(0.15),
            borderRadius: BorderRadius.circular(10),
          ),
          child: Text(
            '$count',
            style: const TextStyle(
              fontSize: 12,
              fontWeight: FontWeight.w600,
              color: AppColors.azureMistDeep,
            ),
          ),
        ),
        const Spacer(),
        if (onAdd != null)
          IconButton(
            icon: const Icon(CupertinoIcons.plus, size: 18),
            color: AppColors.azureMistDeep,
            onPressed: onAdd,
            tooltip: 'Tambah',
          ),
        if (onViewAll != null)
          TextButton(
            onPressed: onViewAll,
            child: const Text('Lihat Semua'),
          ),
      ],
    );
  }
}
