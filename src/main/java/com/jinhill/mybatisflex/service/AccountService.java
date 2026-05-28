package com.jinhill.mybatisflex.service;

import com.jinhill.mybatisflex.domain.entity.Account;
import java.util.List;

public interface AccountService {
    
    Account createAccount(Account account);
    
    Account getAccountById(Long id);
    
    List<Account> getAllAccounts();
    
    Account updateAccount(Long id, Account account);
    
    boolean deleteAccount(Long id);
    
    List<Account> getAccountsByUserName(String userName);
}