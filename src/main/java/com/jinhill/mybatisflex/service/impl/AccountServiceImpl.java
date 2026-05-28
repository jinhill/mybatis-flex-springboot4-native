package com.jinhill.mybatisflex.service.impl;

import com.jinhill.mybatisflex.domain.entity.Account;
import com.jinhill.mybatisflex.domain.mapper.AccountMapper;
import com.jinhill.mybatisflex.service.AccountService;
import com.mybatisflex.core.query.QueryWrapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

import static com.jinhill.mybatisflex.domain.entity.table.AccountTableDef.ACCOUNT;

@Service
public class AccountServiceImpl implements AccountService {
    
    @Autowired
    private AccountMapper accountMapper;
    
    @Override
    public Account createAccount(Account account) {
        accountMapper.insert(account);
        return account;
    }
    
    @Override
    public Account getAccountById(Long id) {
        return accountMapper.selectOneById(id);
    }
    
    @Override
    public List<Account> getAllAccounts() {
        return accountMapper.selectAll();
    }
    
    @Override
    public Account updateAccount(Long id, Account account) {
        account.setId(id);
        accountMapper.update(account);
        return account;
    }
    
    @Override
    public boolean deleteAccount(Long id) {
        return accountMapper.deleteById(id) > 0;
    }
    
    @Override
    public List<Account> getAccountsByUserName(String userName) {
        QueryWrapper queryWrapper = QueryWrapper.create()
                .where(ACCOUNT.USER_NAME.eq(userName));
        return accountMapper.selectListByQuery(queryWrapper);
    }
}