package com.vmware.tanzu.dataflow.realtime.repository;

import com.vmware.tanzu.dataflow.realtime.model.OrderTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface OrderTransactionRepository extends JpaRepository<OrderTransaction, Long> {
    List<OrderTransaction> findTop20ByOrderByProcessedAtDesc();
}
